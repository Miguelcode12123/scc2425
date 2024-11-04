package main.java.tukano.impl;

import static java.lang.String.format;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.errorOrResult;
import static main.java.tukano.api.Result.errorOrValue;
import static main.java.tukano.api.Result.ok;
import static main.java.tukano.api.Result.ErrorCode.BAD_REQUEST;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import main.java.tukano.api.Result;
import main.java.tukano.api.User;
import main.java.tukano.api.UserCosmos;
import main.java.tukano.api.Users;
import main.java.utils.CosmosDB;
import main.java.utils.JSON;
import main.java.utils.RedisCache; // Import for the Cache
import redis.clients.jedis.Jedis;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {}
	
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
				return error(BAD_REQUEST);

		UserCosmos cosmosUser = new UserCosmos(user);

		return errorOrValue( CosmosDB.insertOne( cosmosUser), cosmosUser.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		Log.info( () -> format("Checking if user %s is in cache", userId));
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			// Check if the user is in cache
			String cachedUser = jedis.get("user:" + userId);
			if (cachedUser != null) {
				User userFromCache = JSON.decode(cachedUser, User.class);

				Log.info( () -> format("User %s was in cache", userId));
				return validatedUserOrError(ok(userFromCache), pwd);
			} else {
				Log.info( () -> format("User %s was not found in cache", userId));
				Result<User> result = validatedUserOrError(CosmosDB.getOne(userId, User.class), pwd);

				// Cache the user with 1 hour time expiration (we can change)
				if (result.isOK()) {
					jedis.setex("user:" + userId, 3600, JSON.encode(result.value()));
				}
				return result;
			}
		}
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(CosmosDB.getOne( userId, User.class), pwd), user -> {
			User newUser = user.updateFrom(other);
			UserCosmos cosmosUser = new UserCosmos(newUser);
			Result<User> updatedUser = CosmosDB.updateOne(cosmosUser);

			if(updatedUser.isOK()) {
				// Update the cache again with 1 hour expiration
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.setex("user:" + userId, 3600, JSON.encode(newUser));
					Log.info("User updated in cache \n");
				}
			}

			return updatedUser;
		});
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(CosmosDB.getOne( userId, User.class), pwd), user -> {

			// Remove from cache
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del("user:" + userId);
				Log.info("User removed from cache \n");
			}

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			UserCosmos cosmosUser = new UserCosmos(user);
			return CosmosDB.deleteOne( cosmosUser);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
		Log.info( () -> format("searchUsers : patterns = %s\n", query));
		var hits = CosmosDB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
