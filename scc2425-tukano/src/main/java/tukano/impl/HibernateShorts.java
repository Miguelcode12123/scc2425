package main.java.tukano.impl;

import main.java.tukano.api.*;
import main.java.tukano.api.Short;
import main.java.tukano.api.Result;
import main.java.tukano.api.Short;
import main.java.tukano.api.Shorts;
import main.java.tukano.impl.data.Following;
import main.java.tukano.impl.data.Likes;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.errorOrResult;
import static main.java.tukano.api.Result.errorOrValue;
import static main.java.tukano.api.Result.errorOrVoid;
import static main.java.tukano.api.Result.ok;
import static java.lang.String.format;
import static main.java.tukano.api.Result.ErrorCode.BAD_REQUEST;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.List;
import java.util.logging.Logger;
import main.java.tukano.impl.rest.TukanoRestServer;
import main.java.utils.DB;
import static main.java.utils.DB.getOne;

/**
 * HibernateShorts is an implementation of the Shorts interface using Hibernate.
 */
public class HibernateShorts implements Shorts {

    private static final Logger Log = Logger.getLogger(HibernateShorts.class.getName());
    private static HibernateShorts instance;

    // Singleton pattern to ensure only one instance of HibernateShorts
    synchronized public static Shorts getInstance() {
        if( instance == null )
            instance = new HibernateShorts();
        return instance;
    }

    /**
     * Creates a new short for the given user.
     * It generates a unique ID for the short and stores it in the database.
     *
     * @param userId The ID of the user creating the short
     * @param password The password for the user
     * @return A Result containing the created Short or an error
     */
    @Override
    public Result<Short> createShort(String userId, String password) {
        Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

        // Ensure the user exists and the password is valid
        return errorOrResult( okUser(userId, password), user -> {

            // Create a unique shortId and the blob URL for the short
            var shortId = format("%s+%s", userId, UUID.randomUUID());
            var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);

            // Create the Short object and insert it into the database
            var shrt = new Short(shortId, userId, blobUrl);
            return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
        });
    }

    /**
     * Fetches the details of a short by its ID.
     * Retrieves the number of likes associated with the short.
     *
     * @param shortId The ID of the short
     * @return A Result containing the Short or an error
     */
    @Override
    public Result<Short> getShort(String shortId) {
        Log.info(() -> format("getShort : shortId = %s\n", shortId));

        if( shortId == null )
            return error(BAD_REQUEST);

        // Query to count the number of likes for the short
        var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
        var likes = DB.sql(query, Long.class);

        // Get the Short from the database and return it with the likes count
        return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
    }

    /**
     * Deletes a short from the system.
     * This method requires the password of the user who created the short to verify ownership.
     *
     * @param shortId The ID of the short to delete
     * @param password The password of the user attempting to delete the short
     * @return A Result indicating success or failure
     */
    @Override
    public Result<Void> deleteShort(String shortId, String password) {
        Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

        // Retrieve the short object based on the provided shortId
        return errorOrResult( getShort(shortId), shrt -> {

            // Ensure the user owns the short (verify user and password)
            return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {

                // Perform a database transaction to delete the short and related entities
                return DB.transaction( hibernate -> {

                    // Delete the short
                    hibernate.remove( shrt);

                    // Delete all likes related to the short
                    var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
                    hibernate.createNativeQuery( query, Likes.class).executeUpdate();

                    // Delete the associated blob from the storage
                    JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
                });
            });
        });
    }

    /**
     * Retrieves all shorts belonging to a user.
     *
     * @param userId The ID of the user
     * @return A Result containing a list of short IDs owned by the user
     */
    @Override
    public Result<List<String>> getShorts(String userId) {
        Log.info(() -> format("getShorts : userId = %s\n", userId));

        // Query to get all shortIds for the given user
        var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
        return errorOrValue( okUser(userId), DB.sql( query, String.class));
    }

    /**
     * Follow or unfollow another user.
     * It adds or removes a Following relationship in the database based on the `isFollowing` flag.
     *
     * @param userId1 The ID of the user performing the action
     * @param userId2 The ID of the user being followed or unfollowed
     * @param isFollowing True if following, False if unfollowing
     * @param password The password of the user performing the action
     * @return A Result indicating success or failure
     */
    @Override
    public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
        Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

        // Verify the user's identity
        return errorOrResult( okUser(userId1, password), user -> {
            var f = new Following(userId1, userId2);
            return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));
        });
    }

    /**
     * Retrieves the list of followers for a given user.
     *
     * @param userId The ID of the user
     * @param password The password of the user (to verify ownership)
     * @return A Result containing the list of followers' user IDs
     */
    @Override
    public Result<List<String>> followers(String userId, String password) {
        Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

        // Query to get all followers of the given user
        var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
        return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
    }


    /**
     * Likes or unlikes a short.
     * It adds or removes a Like relationship in the database based on the `isLiked` flag.
     *
     * @param shortId The ID of the short being liked/unliked
     * @param userId The ID of the user performing the action
     * @param isLiked True if liking, False if unliking
     * @param password The password of the user performing the action
     * @return A Result indicating success or failure
     */
    @Override
    public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
        Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

        // Retrieve the short object based on the provided shortId
        return errorOrResult( getShort(shortId), shrt -> {
            var l = new Likes(userId, shortId, shrt.getOwnerId());
            return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
        });
    }

    /**
     * Retrieves a list of users who liked a specific short.
     *
     * @param shortId The ID of the short
     * @param password The password of the short's owner (to verify ownership)
     * @return A Result containing a list of user IDs who liked the short
     */
    @Override
    public Result<List<String>> likes(String shortId, String password) {
        Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

        // Retrieve the short object and ensure the password is correct
        return errorOrResult( getShort(shortId), shrt -> {

            // Query to get all users who liked the short
            var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

            return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
        });
    }

    /**
     * Retrieves the feed of shorts for a user, including their own and those of users they follow.
     *
     * @param userId The ID of the user
     * @param password The password of the user (to verify ownership)
     * @return A Result containing the list of shorts in the user's feed
     */
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

        return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));
    }

    // Helper method to validate a user and password
    protected Result<User> okUser( String userId, String pwd) {
        return JavaUsers.getInstance().getUser(userId, pwd);
    }

    // Helper method to validate a user (password is not required)
    private Result<Void> okUser( String userId ) {
        var res = okUser( userId, "");
        if( res.error() == FORBIDDEN )
            return ok();
        else
            return error( res.error() );
    }

    /**
     * Deletes all shorts and related data (follows, likes) for a user.
     * This operation requires a valid token for security.
     *
     * @param userId The ID of the user whose data is being deleted
     * @param password The password of the user
     * @param token The security token for authorization
     * @return A Result indicating success or failure
     */
    @Override
    public Result<Void> deleteAllShorts(String userId, String password, String token) {
        Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

        // Check if the token is valid
        if( ! Token.isValid( token, userId ) )
            return error(FORBIDDEN);

        // Perform a transaction to delete the shorts, follows, and likes for the user
        return DB.transaction( (hibernate) -> {

            // Delete all shorts owned by the user
            var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
            hibernate.createQuery(query1, Short.class).executeUpdate();

            // Delete all following relationships for the user
            var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
            hibernate.createQuery(query2, Following.class).executeUpdate();

            // Delete all likes related to the user
            var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
            hibernate.createQuery(query3, Likes.class).executeUpdate();

        });
    }
}
