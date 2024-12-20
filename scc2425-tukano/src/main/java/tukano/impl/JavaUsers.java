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

	// Singleton pattern for Users class
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}
	
	private JavaUsers() {}

	// Method to create a new user in the system
	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		// Validate the user's information (checking for missing fields)
		if (badUserInfo(user))
				return error(BAD_REQUEST);

		// Convert user to UserCosmos entity and insert into CosmosDB
		UserCosmos cosmosUser = new UserCosmos(user);

		return errorOrValue( CosmosDB.insertOne( cosmosUser), cosmosUser.getUserId() );
	}

	// Method to retrieve user data by userId and password
	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		// Return error if userId is null
		if (userId == null)
			return error(BAD_REQUEST);

		Log.info( () -> format("Checking if user %s is in cache", userId));
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			// Check if the user is already in Redis cache
			String cachedUser = jedis.get("user:" + userId);
			if (cachedUser != null) {
				// User found in cache, return it after validating the password
				User userFromCache = JSON.decode(cachedUser, User.class);
				Log.info( () -> format("User %s was in cache", userId));
				return validatedUserOrError(ok(userFromCache), pwd);
			} else {
				Log.info( () -> format("User %s was not found in cache", userId));
				// If not in cache, fetch user from CosmosDB and cache it
				Result<User> result = validatedUserOrError(CosmosDB.getOne(userId, User.class), pwd);

				// If user found, cache it for 1 hour
				if (result.isOK()) {
					jedis.setex("user:" + userId, 3600, JSON.encode(result.value()));
				}
				return result;
			}
		}
	}

	// Method to update user information
	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		// Validate user info for update
		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(CosmosDB.getOne( userId, User.class), pwd), user -> {
			// Update user data with new information
			User newUser = user.updateFrom(other);
			UserCosmos cosmosUser = new UserCosmos(newUser);
			// Update user in CosmosDB
			Result<User> updatedUser = CosmosDB.updateOne(cosmosUser);

			if(updatedUser.isOK()) {
				// If updated successfully, update the cache as well
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.setex("user:" + userId, 3600, JSON.encode(newUser));
					Log.info("User updated in cache \n");
				}
			}

			return updatedUser;
		});
	}

	// Method to delete a user by userId and password
	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		// Validate input parameters
		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(CosmosDB.getOne( userId, User.class), pwd), user -> {

			// Remove the user from Redis cache
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del("user:" + userId);
				Log.info("User removed from cache \n");
			}

			// Ensure the cache expiration to avoid stale data
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.expire("user:" + userId, 1); // 1 second expiration as a final fallback
			}

			// Delete user-related blobs and shorts
			JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));

			// Remove the user from CosmosDB
			UserCosmos cosmosUser = new UserCosmos(user);
			return CosmosDB.deleteOne( cosmosUser);
		});
	}

	// Method to search for users based on a pattern (like partial userId)
	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		// Build SQL query to search users based on pattern (case-insensitive)
		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
		Log.info( () -> format("searchUsers : patterns = %s\n", query));

		// Execute query and return matching users, excluding their passwords
		var hits = CosmosDB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		return ok(hits);
	}

	// Helper method to validate user password and return error if it doesn't match
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			// If password matches, return user, otherwise return forbidden error
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res; // Return the error result if user doesn't exist
	}

	// Helper method to check if the user information is valid when creating a new user
	private boolean badUserInfo( User user) {
		// Check if any required field is null (userId, password, displayName, email)
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	// Helper method to check if the user information is valid for updating an existing user
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		// Check if the userId is different in the update info (or is null)
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
