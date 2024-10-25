package main.java.tukano.impl;

import static java.lang.String.format;
import static main.java.tukano.api.Result.ErrorCode.*;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.errorOrResult;
import static main.java.tukano.api.Result.errorOrValue;
import static main.java.tukano.api.Result.errorOrVoid;
import static main.java.tukano.api.Result.ok;
import static main.java.utils.CosmosDB.getOne;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import main.java.tukano.api.Blobs;
import main.java.tukano.api.Result;
import main.java.tukano.api.Short;
import main.java.tukano.api.Shorts;
import main.java.tukano.api.User;
import main.java.tukano.impl.data.Following;
import main.java.tukano.impl.data.Likes;
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

			return errorOrValue(CosmosDB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		try (Jedis jedis = RedisCache.getCachePool().getResource()) {
		 	// Check if the short is in cache
			String cachedShort = jedis.get("short:" + shortId);
			if (cachedShort != null) {
				// Short found in cache, let's decode and return
				return ok(JSON.decode(cachedShort, Short.class));
			} else {
				var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
				var likes = CosmosDB.sql(query, Long.class);
				Result<Short> result = errorOrValue(getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));

				if (result.isOK()) {
					jedis.set("short:" + shortId, JSON.encode(result.value()));
					jedis.expire("short:" + shortId, 3600); // 1 hour expiration
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

				operations.add(() -> CosmosDB.deleteOne(shrt));

				// Delete Likes associated with the Short
				var query = format("SELECT * FROM Likes l WHERE l.shortId = '%s'", shortId);
				var deleteLikesResult = CosmosDB.sql(query, Likes.class);

				for (Likes like : deleteLikesResult) {
					operations.add(() -> CosmosDB.deleteOne(like));
				}
				// Step 5: Delete the blob associated with the Short
				operations.add(() -> JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get()));

				// Clear cache
				try (Jedis jedis = RedisCache.getCachePool().getResource()) {
					jedis.del("short:" + shortId);
				}
				return CosmosDB.transaction(operations, shortId);
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
		return errorOrValue( okUser(userId), CosmosDB.sql( query, String.class));
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));
	
		
		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? CosmosDB.insertOne( f ) : CosmosDB.deleteOne( f ));
		});			
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);		
		return errorOrValue( okUser(userId, password), CosmosDB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		
		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? CosmosDB.insertOne( l ) : CosmosDB.deleteOne( l ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {
			
			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);					
			
			return errorOrValue( okUser( shrt.getOwnerId(), password ), CosmosDB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), CosmosDB.sql( format(QUERY_FMT, userId, userId), String.class));
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
	
	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		if (!okUser(userId, password).isOK()) {
			return error(FORBIDDEN);
		}
		List<Runnable> operations = new ArrayList<>();

		var shortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
		var followersQuery = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
		var likesQuery = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);

		List<Short> shortsList = CosmosDB.sql(shortsQuery, Short.class);
		for (Short shrt : shortsList) {
			operations.add(() -> CosmosDB.deleteOne(shrt));
			// Clear cache for each deleted short
			try (Jedis jedis = RedisCache.getCachePool().getResource()) {
				jedis.del("short:" + shrt.getShortId());
			}
		}

		List<Following> followingList = CosmosDB.sql(followersQuery, Following.class);
		for (Following follow : followingList) {
			operations.add(() -> CosmosDB.deleteOne(follow));
		}

		List<Likes> likesList = CosmosDB.sql(likesQuery, Likes.class);
		for (Likes like : likesList) {
			operations.add(() -> CosmosDB.deleteOne(like));
		}

		return CosmosDB.transaction(operations, userId);
	}
	
}