package main.java.tukano.impl;

import static java.lang.String.format;
import static main.java.tukano.api.Result.ErrorCode.*;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.errorOrResult;
import static main.java.tukano.api.Result.errorOrValue;
import static main.java.tukano.api.Result.errorOrVoid;
import static main.java.tukano.api.Result.ok;
import static main.java.utils.CosmosDB.getOne;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.azure.cosmos.models.CosmosBatch;
import com.azure.cosmos.models.PartitionKey;
import main.java.tukano.api.*;
import main.java.tukano.api.Short;
import main.java.tukano.impl.data.Following;
import main.java.tukano.impl.data.FollowingCosmos;
import main.java.tukano.impl.data.Likes;
import main.java.tukano.impl.data.LikesCosmos;
import main.java.tukano.impl.rest.TukanoRestServer;
import main.java.utils.CosmosDB;
import main.java.utils.JSON;
import main.java.utils.RedisCache;
import redis.clients.jedis.Jedis;


public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());
	
	private static Shorts instance;

	// Singleton instance of JavaShorts class.
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}

	// Method to create a new short for a user.
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		// Verify user credentials before proceeding
		return errorOrResult( okUser(userId, password), user -> {

			// Generate unique shortId and blob URL for the content
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);
			ShortCosmos shrtCosmos = new ShortCosmos(shrt);
			Log.info("BLOB URL UPLOADED: " + blobUrl);

			// Asynchronously upload the blob (media content)
			Executors.defaultThreadFactory().newThread( () -> {
				JavaBlobs.getInstance().upload(shortId, randomBytes(100), Token.get(blobUrl));
			}).start();

			// Insert the new short into CosmosDB and return the result
			return errorOrValue(CosmosDB.insertOne(shrtCosmos), s -> s.copyWithLikes_And_Token(0));
		});
	}

	// Method to retrieve the details of a short using its shortId.
	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		// Handle invalid shortId
		if( shortId == null )
			return error(BAD_REQUEST);

		// Check if short is cached in Redis
		Log.info( () -> format("Checking if short %s is in cache", shortId));
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String cachedShort = jedis.get("short:" + shortId);
			if (cachedShort != null) {
				Log.info( () -> format("Short %s was found in cache", shortId));

				// If cached, return the short with likes
				return ok(getShortWithLikes(JSON.decode(cachedShort, Short.class)));

			} else {
				Log.info( () -> format("Short %s was not found in cache", shortId));

				// If not cached, fetch from CosmosDB and cache the result
				Result<Short> result = getOne(shortId, Short.class);
				Log.info("BLOB URL FROM GET SHORT " + result.value().getBlobUrl());

				if (result.isOK()) {
					jedis.setex("short:" + shortId, 3600, JSON.encode(result.value()));
					return Result.ok(getShortWithLikes(result.value()));
				}
				else {
					return Result.error(result.error());
				}
			}
		}
	}

	// Helper method to retrieve short without token (used for deletion)
	private Result<Short> getShortWithoutToken(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		Log.info( () -> format("Checking if short %s is in cache", shortId));
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String cachedShort = jedis.get("short:" + shortId);
			if (cachedShort != null) {
				Log.info( () -> format("Short %s was found in cache", shortId));

				// If cached, return the short without the associated token
				return ok(JSON.decode(cachedShort, Short.class));

			} else {
				Log.info( () -> format("Short %s was not found in cache", shortId));

				// If not cached, fetch from CosmosDB and cache the result
				Result<Short> result = getOne(shortId, Short.class);
				Log.info("BLOB URL FROM GET SHORT " + result.value().getBlobUrl());

				if (result.isOK()) {
					jedis.setex("short:" + shortId, 3600, JSON.encode(result.value()));
					return Result.ok(result.value());
				}
				else {
					return Result.error(result.error());
				}
			}
		}
	}

	// Method to delete a short based on shortId and user password.
	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		// Fetch the short and verify the user credentials before deletion
		return errorOrResult( getShortWithoutToken(shortId), shrt -> {
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				List<Runnable> operations = new ArrayList<>();

				// Query and delete likes associated with the short
				var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", shortId);
				var deleteLikesResult = CosmosDB.sql(query, Likes.class);

				for (Likes l : deleteLikesResult) {
					LikesCosmos lCosmos = new LikesCosmos(l);
					operations.add(() -> CosmosDB.deleteOne(lCosmos));
				}

				// Asynchronously delete the blob associated with the short
				Executors.defaultThreadFactory().newThread( () -> {
					Log.info("BLOB URL DELETED: " + shrt.getBlobUrl());
					JavaBlobs.getInstance().delete(shrt.getShortId(), Token.get(shrt.getBlobUrl()));
				}).start();

				// Clear the short from the Redis cache
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del("short:" + shortId);
				}

				// Delete the short metadata from CosmosDB
				ShortCosmos shrtCosmos = new ShortCosmos(shrt);
				operations.add(() -> CosmosDB.deleteOne(shrtCosmos));

				// Run all the operations in a batch
				return CosmosDB.runOperations(operations);
			});
		});
	}

	// Method to fetch all shortIds of a specific user
	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);

		List<String> arr = new ArrayList<>();
		List<Short> results = CosmosDB.sql( query, Short.class);

		// Collect all short IDs from the query results
		for (Short shrt : results) {
            arr.add(shrt.getShortId());
        }

		return errorOrValue( okUser(userId), arr);
	}

	// Method to follow or unfollow another user
	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		// Verify the user credentials before proceeding
		return errorOrResult( okUser(userId1, password), user -> {
			Following f = new Following(userId1, userId2);
			FollowingCosmos fCosmos = new FollowingCosmos(f);
			// Add or remove the following relationship in CosmosDB based on isFollowing
			return errorOrVoid( okUser( userId2), isFollowing ? CosmosDB.insertOne( fCosmos ) : CosmosDB.deleteOne( fCosmos ));
		});			
	}

	// Method to get a list of followers of a specific user
	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		// Verify user credentials before fetching followers
		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s' AND f.follower != null", userId);
		List<Following> followers = CosmosDB.sql(query, Following.class);
		List<String> results = new ArrayList<>();

		// Collect all followers from the query results
		for(Following f : followers) {
			results.add(f.getFollower());
		}
		return errorOrValue( okUser(userId, password), results);
	}

	// Method to "like" or "unlike" a short.
	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			Likes l = new Likes(userId, shortId, shrt.getOwnerId());
			LikesCosmos lCosmos = new LikesCosmos(l);
			// Add or remove the like from CosmosDB
			return errorOrVoid( okUser( userId, password), isLiked ? CosmosDB.insertOne( lCosmos ) : CosmosDB.deleteOne( lCosmos ));
		});
	}

	// Method to get a list of users who liked a specific short.
	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", shortId);
			List<Likes> likes = CosmosDB.sql(query, Likes.class);
			List<String> results = new ArrayList<>();
			// Collect the list of users who liked the short
			for(Likes l : likes) {
				results.add(l.getUserId());
			}
			return errorOrValue( okUser( shrt.getOwnerId(), password ),results);
		});
	}

	// Method to get the feed for a user, including their own shorts and the shorts of users they follow.
	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		var ownerQuery = format("SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = '%s' AND s.shortId != null AND s.timestamp != 0 ORDER BY s.timestamp DESC", userId);
		List<Short> shortsOwner = CosmosDB.sql(ownerQuery, Short.class);

		var followeeQuery = format("SELECT f.followee FROM Following f WHERE f.follower = '%s' AND f.followee != null", userId);
		List<Following> followeeIds = CosmosDB.sql(followeeQuery, Following.class);

		// Retrieve all shorts by the user
		List<Short> shortsFollowing = new ArrayList<>();
		for (Following followee : followeeIds) {
			var followingQuery = format("SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = '%s' AND s.shortId != null ORDER BY s.timestamp DESC", followee.getFollowee());
			shortsFollowing.addAll(CosmosDB.sql(followingQuery, Short.class));
		}
		List<String> results = new ArrayList<>();

		List<Short> combinedShorts = new ArrayList<>(shortsOwner);
		combinedShorts.addAll(shortsFollowing);
		combinedShorts.sort((s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));

		for(Short shrt: combinedShorts) {
			results.add(format("Short %s with timestamp %s", shrt.getShortId(), shrt.getTimestamp()));
		}

		return errorOrValue( okUser( userId, password), results);
	}
		
	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	// Helper method to check if user is valid
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}

	// Helper method to get short details with likes count
	private Short getShortWithLikes(Short s) {
		var query = format("SELECT VALUE COUNT(1) FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", s.getShortId());
		var likes = CosmosDB.sql(query, Long.class);
		return s.copyWithLikes_And_Token( likes.get(0));
	}

	// Method to delete all shorts of a user
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		// Verify if the token is valid
		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		// Verify user credentials
		if (!okUser(userId, password).isOK()) {
			return error(FORBIDDEN);
		}
		List<Runnable> operations = new ArrayList<>();

		var shortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s' AND s.blobUrl != null", userId);
		var followersQuery = format("SELECT * FROM Following f WHERE f.follower = '%s' AND f.followee != null OR f.followee = '%s' AND f.follower != null", userId, userId);
		var likesQuery = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' AND l.userId != null OR l.userId = '%s' AND l.ownerId !=null", userId, userId);

		List<Following> followingList = CosmosDB.sql(followersQuery, Following.class);
		for (Following follow : followingList) {
			FollowingCosmos fCosmos = new FollowingCosmos(follow);
			operations.add(() -> CosmosDB.deleteOne(fCosmos));
		}

		List<Likes> likesList = CosmosDB.sql(likesQuery, Likes.class);
		for (Likes l : likesList) {
			LikesCosmos lCosmos = new LikesCosmos(l);
			operations.add(() -> CosmosDB.deleteOne(lCosmos));
		}

		List<Short> shortsList = CosmosDB.sql(shortsQuery, Short.class);
		for (Short shrt : shortsList) {
			// Clear cache for each deleted short
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del("short:" + shrt.getShortId());
			}

			ShortCosmos shrtCosmos = new ShortCosmos(shrt);
			operations.add(() -> CosmosDB.deleteOne(shrtCosmos));
		}

		return CosmosDB.runOperations(operations);
	}

	// Helper method to generate random bytes, likely used for blob content
	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);

		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));

		return bb.array();

	}
	
}