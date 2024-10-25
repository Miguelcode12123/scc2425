package main.java.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
    private static final String RedisHostname = System.getenv("REDIS_HOSTNAME"); // Change for "redis_hostname
    private static final String RedisKey = System.getenv("REDIS_KEY"); // Change for the key
    private static final int REDIS_PORT = 6380;  // Default for Azure
    private static final int REDIS_TIMEOUT = 1000;
    private static final boolean Redis_USE_TLS = true;

    private static JedisPool instance;

    public synchronized static JedisPool getCachePool() {
        if (instance != null)
            return instance;

        var poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        try {
            instance = new JedisPool(poolConfig, RedisHostname, REDIS_PORT, REDIS_TIMEOUT, RedisKey, Redis_USE_TLS);
        } catch(Exception e) {
            System.err.println("Error creating Jedis pool: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Unable to create Redis pool", e);  // Re-throw or handle the exception
        }
        return instance;
    } 
}