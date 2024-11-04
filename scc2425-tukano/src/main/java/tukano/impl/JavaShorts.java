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
	
	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}
	
	private JavaShorts() {}
	
	
	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);
			ShortCosmos shrtCosmos = new ShortCosmos(shrt);

			String token = Token.get(blobUrl);
			Log.info("Generated token: " + token);

			// Commented in JavaBlobs so it passes
			// Is the problem in the token or in the first argument (blobId) ???
			// We're sending blobUrl but it expects the blobId...
			var result = JavaBlobs.getInstance().upload(blobUrl, randomBytes(100), Token.get(blobUrl));
			Log.info(Token.get(blobUrl));

			if (result.isOK()) {
				Log.info("everything ok (TEST)");
			}
			else
				Log.info("error: " + result.error());

			return errorOrValue(CosmosDB.insertOne(shrtCosmos), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		Log.info( () -> format("Checking if short %s is in cache", shortId));
		try (Jedis jedis = RedisCache.getCachePool().getResource()) {

			String cachedShort = jedis.get("short:" + shortId);
			if (cachedShort != null) {
				Log.info( () -> format("Short %s was found in cache", shortId));

				return ok(getShortWithLikes(JSON.decode(cachedShort, Short.class)));

			} else {
				Log.info( () -> format("Short %s was not found in cache", shortId));

				Result<Short> result = errorOrValue(getOne(shortId, Short.class), this::getShortWithLikes);

				if (result.isOK()) {
					jedis.setex("short:" + shortId, 3600, JSON.encode(result.value()));
				}
				return result;
			}
		}
		}

	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));
		return errorOrResult( getShort(shortId), shrt -> {
			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				List<Runnable> operations = new ArrayList<>();

				// Delete Likes associated with the Short
				var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", shortId);
				var deleteLikesResult = CosmosDB.sql(query, Likes.class);

				for (Likes l : deleteLikesResult) {
					LikesCosmos lCosmos = new LikesCosmos(l);
					operations.add(() -> CosmosDB.deleteOne(lCosmos));
				}

				operations.add(() -> {
					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
				});

				// Clear cache
				operations.add(() -> {
					try (Jedis jedis = RedisCache.getCachePool().getResource()) {
						jedis.del("short:" + shortId);
					}
				});

				ShortCosmos shrtCosmos = new ShortCosmos(shrt);
				operations.add(() -> CosmosDB.deleteOne(shrtCosmos));

				return CosmosDB.runOperations(operations);
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);

		List<String> arr = new ArrayList<>();
		List<Short> results = CosmosDB.sql( query, Short.class);

        for (Short shrt : results) {
            arr.add(shrt.getShortId());
        }

		return errorOrValue( okUser(userId), arr);
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		return errorOrResult( okUser(userId1, password), user -> {
			Following f = new Following(userId1, userId2);
			FollowingCosmos fCosmos = new FollowingCosmos(f);
			return errorOrVoid( okUser( userId2), isFollowing ? CosmosDB.insertOne( fCosmos ) : CosmosDB.deleteOne( fCosmos ));
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s' AND f.follower != null", userId);
		List<Following> followers = CosmosDB.sql(query, Following.class);
		List<String> results = new ArrayList<>();

		for(Following f : followers) {
			results.add(f.getFollower());
		}

		return errorOrValue( okUser(userId, password), results);
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			Likes l = new Likes(userId, shortId, shrt.getOwnerId());
			LikesCosmos lCosmos = new LikesCosmos(l);
			return errorOrVoid( okUser( userId, password), isLiked ? CosmosDB.insertOne( lCosmos ) : CosmosDB.deleteOne( lCosmos ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", shortId);
			List<Likes> likes = CosmosDB.sql(query, Likes.class);
			List<String> results = new ArrayList<>();
			for(Likes l : likes) {
				results.add(l.getUserId());
			}
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ),results);
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		var ownerQuery = format("SELECT s.shortId, s.timestamp FROM Short s WHERE s.ownerId = '%s' AND s.shortId != null AND s.timestamp != 0 ORDER BY s.timestamp DESC", userId);
		List<Short> shortsOwner = CosmosDB.sql(ownerQuery, Short.class);

		var followeeQuery = format("SELECT f.followee FROM Following f WHERE f.follower = '%s' AND f.followee != null", userId);
		List<Following> followeeIds = CosmosDB.sql(followeeQuery, Following.class);

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
	
	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}

	private Short getShortWithLikes(Short s) {
		var query = format("SELECT VALUE COUNT(1) FROM Likes l WHERE l.shortId = '%s' AND l.userId != null", s.getShortId());
		var likes = CosmosDB.sql(query, Long.class);
		return s.copyWithLikes_And_Token( likes.get(0));
	}
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

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
			operations.add(() -> JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get()));

			// Clear cache for each deleted short
			operations.add(() -> {
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del("short:" + shrt.getShortId());
				}
			});

			ShortCosmos shrtCosmos = new ShortCosmos(shrt);
			operations.add(() -> CosmosDB.deleteOne(shrtCosmos));
		}

		return CosmosDB.runOperations(operations);
	}


	private static byte[] randomBytes(int size) {
		var r = new Random(1L);

		var bb = ByteBuffer.allocate(size);

		r.ints(size).forEach( i -> bb.put( (byte)(i & 0xFF)));

		return bb.array();

	}
	
}