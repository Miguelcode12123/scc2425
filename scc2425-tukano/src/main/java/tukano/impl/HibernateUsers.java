package main.java.tukano.impl;

import main.java.tukano.api.Result;
import main.java.tukano.api.User;
import main.java.tukano.api.Users;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.errorOrResult;
import static main.java.tukano.api.Result.errorOrValue;
import static main.java.tukano.api.Result.ok;
import static java.lang.String.format;
import static main.java.tukano.api.Result.ErrorCode.BAD_REQUEST;
import static main.java.tukano.api.Result.ErrorCode.FORBIDDEN;
import java.util.concurrent.Executors;

import java.util.List;
import java.util.logging.Logger;
import main.java.utils.DB;

/**
 * HibernateUsers is an implementation of the Users interface using Hibernate.
 */
public class HibernateUsers implements Users {

    private static final Logger Log = Logger.getLogger(HibernateUsers.class.getName());
    private static Users instance;

    // Singleton pattern to ensure only one instance of HibernateUsers exists
    synchronized public static Users getInstance() {
        if( instance == null )
            instance = new HibernateUsers();
        return instance;
    }

    // Private constructor to prevent direct instantiation
    private HibernateUsers() {}

    /** Create a new user in the system
     * @param user - User to be created
     * @return A Result containing the created User or an error
     */
    @Override
    public Result<String> createUser(User user) {
        Log.info(() -> format("createUser : %s\n", user));

        // Validate the user information before inserting into the database
        if(badUserInfo(user))
            return error(BAD_REQUEST);

        // Insert the user into the database and return the userId if successful
        return errorOrValue(DB.insertOne(user), user.getUserId());
    }

    /**
     * Retrieve a user by their ID and password
     * @param userId - User ID to retrieve
     * @param pwd - User's password for validation
     * @return A Result containing the User if found and validated, or an error if not found or invalid credentials
     */
    @Override
    public Result<User> getUser(String userId, String pwd) {
        Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

        // Check if the userId is null and return a BAD_REQUEST error if so
        if (userId == null)
            return error(BAD_REQUEST);

        // Validate the user credentials (check if password matches)
        return validatedUserOrError( DB.getOne( userId, User.class), pwd);
    }


    /**
     * Update a user's information
     * @param userId - User ID of the user to be updated
     * @param pwd - User's password for validation
     * @param other - User object containing the new information to update
     * @return A Result containing the updated User or an error if validation fails or update fails
     */
    @Override
    public Result<User> updateUser(String userId, String pwd, User other) {
        Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

        // Validate the provided user information before updating
        if (badUpdateUserInfo(userId, pwd, other))
            return error(BAD_REQUEST);

        // Validate the user's credentials, then update the user information in the database
        return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> DB.updateOne( user.updateFrom(other)));
    }

    /**
     * Delete a user from the system
     * @param userId - User ID of the user to be deleted
     * @param pwd - User's password for validation
     * @return A Result indicating whether the deletion was successful or an error
     */
    @Override
    public Result<User> deleteUser(String userId, String pwd) {
        Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

        // Return a BAD_REQUEST error if either userId or password is null
        if (userId == null || pwd == null )
            return error(BAD_REQUEST);

        // Validate the user's credentials before proceeding with deletion
        return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

            // Delete user shorts and related info asynchronously in a separate thread
            Executors.defaultThreadFactory().newThread( () -> {
                JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
                JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
            }).start();

            // Delete the user record from the database
            return DB.deleteOne( user);
        });
    }

    /**
     * Search for users based on a search pattern
     * @param pattern - Search pattern to match against user IDs
     * @return A Result containing a list of matching users, excluding their passwords, or an error if no users are found
     */
    @Override
    public Result<List<User>> searchUsers(String pattern) {
        Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

        // Construct a SQL query to search for userIds matching the pattern (case-insensitive search)
        var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());

        // Execute the query and map the results, removing the password field for security
        var hits = DB.sql(query, User.class)
                .stream()
                .map(User::copyWithoutPassword)  // Remove the password from user data before returning
                .toList();

        // Return the list of matching users
        return ok(hits);
    }

    /**
     * Auxiliary method to check if the password of the user matches
     * @param res - The Result
     * @param pwd - Password to check
     * @return
     */
    private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
        if( res.isOK())
            return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
        else
            return res;
    }

    /**
     * Auxiliary method to check if the info's of the user aren't empty
     * @param user - The user to check
     * @return true if everything is ok.
     */
    private boolean badUserInfo( User user) {
        return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
    }

    /**
     * Auxiliary method to check if the info's of the user is okay to update
     * @param userId - the userId of the user
     * @param pwd - the password of the user
     * @param info - the info to change of the user
     * @return
     */
    private boolean badUpdateUserInfo( String userId, String pwd, User info) {
        return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
    }
}
