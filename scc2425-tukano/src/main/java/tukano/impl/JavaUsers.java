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

		return errorOrValue( CosmosDB.insertOne( user), user.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
			// Check if the user is in cache
			String cachedUser = jedis.get("user:" + userId);
			if (cachedUser != null) {
				// User found in cache, let's decode and return
				User userFromCache = JSON.decode(cachedUser, User.class);
				return validatedUserOrError(ok(userFromCache), pwd);
			} else {
				// User not in cache, fetch from database
				Result<User> result = validatedUserOrError(CosmosDB.getOne(userId, User.class), pwd);

				// Cache the user with 1 hour time expiration (we can change)
				if (result.isOK()) {
					jedis.set("user:" + userId, JSON.encode(result.value()));
					jedis.expire("user:" + userId, 3600);
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
			Result<User> updatedUser = CosmosDB.updateOne(user.updateFrom(other));

			// Update the cache again with 1 hour expiration
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.set("user:" + userId, JSON.encode(updatedUser));
				jedis.expire("user:" + userId, 3600); // 1 hour expiration
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

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			// Remove from cache
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del("user" + userId);
			}

			return CosmosDB.deleteOne( user);
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
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
