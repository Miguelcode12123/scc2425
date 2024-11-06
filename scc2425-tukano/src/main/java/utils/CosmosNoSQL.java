package main.java.utils;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import main.java.tukano.api.Result;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import main.java.tukano.api.ShortCosmos;
import main.java.tukano.api.Short;
import main.java.tukano.api.User;
import main.java.tukano.api.UserCosmos;
import main.java.tukano.impl.data.Following;
import main.java.tukano.impl.data.FollowingCosmos;
import main.java.tukano.impl.data.Likes;
import main.java.tukano.impl.data.LikesCosmos;

public class CosmosNoSQL {

    private static final String CONNECTION_URL = "https://cosmos7066270663.documents.azure.com:443/"; // replace with your own
    private static final String DB_KEY = "ZU8GyphhOkRCUcqFlEUrDdhqDuILhyX9tRzbWmr0hbHkZwahcvUZF5P7BovcGPrsEAuiKYarckNTACDb80vegQ==";
    private static final String DB_NAME = "scc7066270663";
    private static final String USER_CONTAINER = "users";
    private static final String SHORTS_CONTAINER = "shorts";
    private static final String LIKES_CONTAINER = "likes";
    private static final String FOLLOWING_CONTAINER = "following";

    private static CosmosNoSQL instance;

    public static synchronized CosmosNoSQL getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                //.directMode()
                .gatewayMode()
                // replace by .directMode() for better performance
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new CosmosNoSQL( client);
        return instance;

    }

    private CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer selectedContainer;
    private CosmosContainer usersContainer;
    private CosmosContainer shortsContainer;
    private CosmosContainer likesContainer;
    private CosmosContainer followingContainer;
    private Map<Class<?>, CosmosContainer> containerMap;

    public CosmosNoSQL(CosmosClient client) {
        this.client = client;
    }

    private synchronized void init() {
        if( db != null)
            return;
        this.db = client.getDatabase(DB_NAME);
        this.usersContainer = db.getContainer(USER_CONTAINER);
        this.shortsContainer = db.getContainer(SHORTS_CONTAINER);
        this.likesContainer = db.getContainer(LIKES_CONTAINER);
        this.followingContainer = db.getContainer(FOLLOWING_CONTAINER);
        this.containerMap = new HashMap<>() {{
            put(UserCosmos.class, usersContainer);
            put(ShortCosmos.class, shortsContainer);
            put(LikesCosmos.class, likesContainer);
            put(FollowingCosmos.class, followingContainer);
            put(User.class, usersContainer);
            put(Short.class, shortsContainer);
            put(Likes.class, likesContainer);
            put(Following.class, followingContainer);
            put(Long.class, shortsContainer);
        }};
    }

    public void close() {
        client.close();
    }

    public <T> Result<List<T>> sql(String queryStr, Class<T> clazz) {
        return query(clazz, queryStr);
    }

    public <T> Result<List<T>> sql(Class<T> clazz, String fmt, Object... args) {
        String formattedQuery = String.format(fmt, args);
        return sql(formattedQuery, clazz);
    }

    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch( () -> {
            selectContainerClass(clazz);
            System.err.println("OBJECT CLASS " + clazz);
            return this.selectedContainer.readItem(id, new PartitionKey(id), clazz).getItem();
        });
    }

    public <T> Result<T> deleteOne(T obj) {
        init();
        selectContainerObj(obj);
        var result = this.selectedContainer.deleteItem(obj, new CosmosItemRequestOptions());
        if(result.getStatusCode() == 204) {
            return Result.ok(obj);
        }
        else {
            return Result.error(errorCodeFromStatus(result.getStatusCode()));
        }
    }

    public <T> Result<T> updateOne(T obj) {
        return tryCatch( () -> {
            selectContainerObj(obj);
            return this.selectedContainer.upsertItem(obj).getItem();
        });
    }

    public <T> Result<T> insertOne( T obj) {
        return tryCatch( () -> {
            selectContainerObj(obj);
            return this.selectedContainer.createItem(obj).getItem();
        });
    }

    public <T> Result<T> runOperations(List<Runnable> operations) {
        for (Runnable operation : operations) {
            operation.run();
        }
        return Result.ok();
    }

    private <T> Result<List<T>> query(Class<T> clazz, String queryStr) {
        return tryCatch(() -> {
            selectContainerClass(clazz);
            var res = this.selectedContainer.queryItems(queryStr, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    private <T> void selectContainerObj(T obj) {
        this.selectedContainer = containerMap.get(obj.getClass());
    }
    private <T> void selectContainerClass(Class<T> clazz) {
        this.selectedContainer = containerMap.get(clazz);
    }

    <T> Result<T> tryCatch( Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            System.err.println("Cosmos Exception caused by: ");
            ce.printStackTrace();
            return Result.error ( errorCodeFromStatus(ce.getStatusCode() ));
        } catch( Exception x ) {
            System.err.println("Exception caused by: ");
            x.printStackTrace();
            return Result.error( Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    static Result.ErrorCode errorCodeFromStatus( int status ) {
        return switch( status ) {
            case 200 -> Result.ErrorCode.OK;
            case 404 -> Result.ErrorCode.NOT_FOUND;
            case 409 -> Result.ErrorCode.CONFLICT;
            default -> Result.ErrorCode.INTERNAL_ERROR;
        };
    }

}
