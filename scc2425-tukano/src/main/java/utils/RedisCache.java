package main.java.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


/**
 * A utility class for managing Redis cache connections using Jedis.
 * This class provides a singleton JedisPool instance for connecting to the Redis server.
 */
public class RedisCache {
    private static final String RedisHostname = "scc7066270663cache.redis.cache.windows.net"; // Redis server hostname
    private static final String RedisKey = "deDbQ5aU4YlQek6WlIiUTzvOtFZStkTVuAzCaPaD6UA="; // Redis authentication key
    private static final int REDIS_PORT = 6380;  // Default for Azure
    private static final int REDIS_TIMEOUT = 5000;
    private static final boolean Redis_USE_TLS = true;

    // Singleton instance for JedisPool
    private static JedisPool instance;


    /**
     * Returns the JedisPool instance for Redis cache access.
     * This method ensures that only one instance of JedisPool is created, implementing the Singleton pattern.
     *
     * @return JedisPool instance for Redis cache
     * @throws RuntimeException if there is an error creating the JedisPool
     */
    public synchronized static JedisPool getCachePool() {
        if (instance != null)
            return instance;

        // Configure JedisPool settings for connection pooling
        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);  // Max number of connections that can be allocated by the pool
        poolConfig.setMaxIdle(128);  // Max number of idle connections in the pool
        poolConfig.setMinIdle(16);   // Min number of idle connections in the pool
        poolConfig.setTestOnBorrow(true);  // Test connections before borrowing them from the pool
        poolConfig.setTestOnReturn(true);  // Test connections before returning them to the pool
        poolConfig.setTestWhileIdle(true);  // Test connections while idle to ensure they are valid
        poolConfig.setNumTestsPerEvictionRun(3);  // Number of tests per eviction run
        poolConfig.setBlockWhenExhausted(true);  // Block when the pool is exhausted and no connections are available

        // Attempt to create the JedisPool with the provided configuration
        try {
            // Create a JedisPool instance with the configured settings and Redis connection details
            instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
        } catch(Exception e) {
            System.err.println("Error creating Jedis pool: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unable to create Redis pool", e);  // Re-throw or handle the exception
        }
        // Return the JedisPool instance
        return instance;
    } 
}
