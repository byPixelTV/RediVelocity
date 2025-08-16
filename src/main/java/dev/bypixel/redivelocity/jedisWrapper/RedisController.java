/*
 * Copyright (c) 2025.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.jedisWrapper;

import dev.bypixel.redivelocity.RediVelocityLogger;
import dev.bypixel.redivelocity.config.Config;
import dev.bypixel.redivelocity.config.ConfigLoader;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.json.JSONObject;
import redis.clients.jedis.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class RedisController extends BinaryJedisPubSub implements Runnable {

    private final RediVelocityLogger rediVelocityLogger;
    @Getter
    private JedisPool jedisPool;
    private final AtomicBoolean isConnectionBroken = new AtomicBoolean(true);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    @Getter
    private JedisCluster jedisCluster;

    @Inject
    public RedisController(RediVelocityLogger rediVelocityLogger) {
        this.rediVelocityLogger = rediVelocityLogger;

        ConfigLoader configLoader = new ConfigLoader(rediVelocityLogger);
        configLoader.load();
        Config config = configLoader.getConfig();

        boolean isCluster = config.getRedis().isCluster();
        JedisPoolConfig jConfig = new JedisPoolConfig();
        int maxConnections = 10;
        jConfig.setMaxTotal(maxConnections);
        jConfig.setMaxIdle(maxConnections);
        jConfig.setMinIdle(1);
        jConfig.setBlockWhenExhausted(true);
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setMaxIdle(maxConnections);
        poolConfig.setMinIdle(1);
        poolConfig.setBlockWhenExhausted(true);

        try {
            if (isCluster) {
                String host = config.getRedis().getHost();
                int port = config.getRedis().getPort();
                Set<HostAndPort> nodes = Set.of(new HostAndPort(host, port));
                String password = config.getRedis().getPassword();
                if (password.isEmpty()) {
                    this.jedisCluster = new JedisCluster(nodes, 2000, 2000, 5, poolConfig);
                } else {
                    this.jedisCluster = new JedisCluster(nodes, 2000, 2000, 5, password, poolConfig);
                }
            } else {
                String password = config.getRedis().getPassword();
                if (password.isEmpty()) {
                    this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort());
                } else {
                    this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort(), 2000, password);
                }
            }
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("Failed to initialize RedisController: " + e.getMessage());
        }

        run();
    }

    @Override
    public void run() {
        if (!isConnectionBroken.get() || isConnecting.get()) {
            return;
        }
        rediVelocityLogger.sendLogs("Connecting to Redis server...");
        isConnecting.set(true);

        CompletableFuture.runAsync(() -> {
            if (jedisCluster != null) {
                try (var ignored = jedisCluster.getConnectionFromSlot(0)) {
                    isConnectionBroken.set(false);
                    rediVelocityLogger.sendConsoleMessage("<green>Successfully connected to Redis Cluster.</green>");
                } catch (Exception e) {
                    isConnecting.set(false);
                    isConnectionBroken.set(true);
                    rediVelocityLogger.sendErrorLogs("Connection to Redis Cluster has failed: " + e.getMessage());
                }
            } else {
                try (var ignored = jedisPool.getResource()) {
                    isConnectionBroken.set(false);
                    rediVelocityLogger.sendConsoleMessage("<green>Successfully connected to Redis server.</green>");
                } catch (Exception e) {
                    isConnecting.set(false);
                    isConnectionBroken.set(true);
                    rediVelocityLogger.sendErrorLogs("Connection to Redis server has failed: " + e.getMessage());
                }
            }
        });
    }

    public void shutdown() {
        rediVelocityLogger.sendLogs("Shutting down Redis connection...");
        if (jedisPool != null) {
            jedisPool.close();
        }
        if (jedisCluster != null) {
            try {
                jedisCluster.close();
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to close JedisCluster: " + e.getMessage());
            }
        }
        rediVelocityLogger.sendLogs("Redis connection has been shut down.");
    }

    public void sendPostLoginMessage(String event, String proxyId, String username, String useruuid, String userip, String channel) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", event);
        jsonObject.put("proxy", proxyId);
        jsonObject.put("name", username);
        jsonObject.put("uuid", useruuid);
        jsonObject.put("address", userip);
        jsonObject.put("timestamp", System.currentTimeMillis());
        String jsonString = jsonObject.toString();

        if (jedisCluster != null) {
            try {
                jedisCluster.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send post-login Redis message: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send post-login Redis message: " + e.getMessage());
            }
        }
    }

    public void sendServerSwitchMessage(String event, String proxyId, String username, String useruuid, String clientbrand, String userip, String serverName, String previousServer, String channel) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", event);
        jsonObject.put("proxy", proxyId);
        jsonObject.put("name", username);
        jsonObject.put("uuid", useruuid);
        jsonObject.put("clientbrand", clientbrand);
        jsonObject.put("address", userip);
        jsonObject.put("timestamp", System.currentTimeMillis());
        jsonObject.put("server", serverName);
        jsonObject.put("previousServer", previousServer);

        String jsonString = jsonObject.toString();

        if (jedisCluster != null) {
            try {
                jedisCluster.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send server switch Redis message: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send server switch Redis message: " + e.getMessage());
            }
        }
    }

    public void sendJsonMessage(String event, String proxyId, String username, String useruuid, String clientbrand, String userip, String channel) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("action", event);
        jsonObject.put("proxy", proxyId);
        jsonObject.put("name", username);
        jsonObject.put("uuid", useruuid);
        jsonObject.put("clientbrand", clientbrand);
        jsonObject.put("address", userip);
        jsonObject.put("timestamp", System.currentTimeMillis());
        String jsonString = jsonObject.toString();

        if (jedisCluster != null) {
            try {
                jedisCluster.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send JSON Redis message: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.publish(channel, jsonString);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to send JSON Redis message: " + e.getMessage());
            }
        }
    }

    public void setHashField(String hashName, String fieldName, String value) {
        if (jedisCluster != null) {
            String type = jedisCluster.type(hashName);
            if (!"hash".equals(type)) {
                if ("none".equals(type)) {
                    jedisCluster.hset(hashName, fieldName, value);
                } else {
                    rediVelocityLogger.sendErrorLogs("Error: Key " + hashName + " doesn't hold a hash. It holds a " + type + ".");
                }
            } else {
                jedisCluster.hset(hashName, fieldName, value);
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                String type = jedis.type(hashName);
                if (!"hash".equals(type)) {
                    if ("none".equals(type)) {
                        jedis.hset(hashName, fieldName, value);
                    } else {
                        rediVelocityLogger.sendErrorLogs("Error: Key " + hashName + " doesn't hold a hash. It holds a " + type + ".");
                    }
                } else {
                    jedis.hset(hashName, fieldName, value);
                }
            }
        }
    }

    public void deleteHashField(String hashName, String fieldName) {
        if (jedisCluster != null) {
            jedisCluster.hdel(hashName, fieldName);
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.hdel(hashName, fieldName);
            }
        }
    }

    public void deleteHash(String hashName) {
        if (jedisCluster != null) {
            jedisCluster.del(hashName);
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.del(hashName);
            }
        }
    }

    public Map<String, String> getHashValuesAsPair(String hashName) {
        Map<String, String> values = new HashMap<>();
        if (jedisCluster != null) {
            try {
                Set<String> keys = jedisCluster.hkeys(hashName);
                for (String key : keys) {
                    values.put(key, jedisCluster.hget(hashName, key));
                }
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get hash values: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                Set<String> keys = jedis.hkeys(hashName);
                for (String key : keys) {
                    values.put(key, jedis.hget(hashName, key));
                }
            }
        }
        return values;
    }

    public void setString(String key, String value) {
        if (jedisCluster != null) {
            try {
                jedisCluster.set(key, value);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to set string in Redis: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.set(key, value);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to set string in Redis: " + e.getMessage());
            }
        }
    }

    public void deleteString(String key) {
        if (jedisCluster != null) {
            try {
                jedisCluster.del(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to delete string in Redis: " + e.getMessage());
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to delete string in Redis: " + e.getMessage());
            }
        }
    }

    public void deleteHashFieldByBalue(String hashName, String value) {
        if (jedisCluster != null) {
            Set<String> keys = jedisCluster.hkeys(hashName);
            for (String key : keys) {
                if (jedisCluster.hget(hashName, key).equals(value)) {
                    jedisCluster.hdel(hashName, key);
                    return;
                }
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                Set<String> keys = jedis.hkeys(hashName);
                for (String key : keys) {
                    if (jedis.hget(hashName, key).equals(value)) {
                        jedis.hdel(hashName, key);
                        return;
                    }
                }
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to delete hash field by value: " + e.getMessage());
            }
        }
    }

    public String getString(String key) {
        if (jedisCluster != null) {
            try {
                return jedisCluster.get(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get string from Redis: " + e.getMessage());
                return null;
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                return jedis.get(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get string from Redis: " + e.getMessage());
                return null;
            }
        }
    }

    public String getHashField(String hashName, String fieldName) {
        if (jedisCluster != null) {
            try {
                return jedisCluster.hget(hashName, fieldName);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get hash field from Redis: " + e.getMessage());
                return null;
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                return jedis.hget(hashName, fieldName);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get hash field from Redis: " + e.getMessage());
                return null;
            }
        }
    }

    public Set<String> getAllHashFields(String hashName) {
        if (jedisCluster != null) {
            try {
                return jedisCluster.hkeys(hashName);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get all hash fields from Redis: " + e.getMessage());
                return Collections.emptySet();
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                return jedis.hkeys(hashName);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get all hash fields from Redis: " + e.getMessage());
                return Collections.emptySet();
            }
        }
    }

    public List<String> getAllHashValues(String hashName) {
        if (jedisCluster != null) {
            try {
                return new ArrayList<>(jedisCluster.hvals(hashName));
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get all hash values from Redis: " + e.getMessage());
                return Collections.emptyList();
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                return new ArrayList<>(jedis.hvals(hashName));
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get all hash values from Redis: " + e.getMessage());
                return Collections.emptyList();
            }
        }
    }

    public String getHashKeyByValue(String hashName, String value) {
        if (jedisCluster != null) {
            Set<String> keys = jedisCluster.hkeys(hashName);
            for (String key : keys) {
                if (jedisCluster.hget(hashName, key).equals(value)) {
                    return key;
                }
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                Set<String> keys = jedis.hkeys(hashName);
                for (String key : keys) {
                    if (jedis.hget(hashName, key).equals(value)) {
                        return key;
                    }
                }
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to get hash key by value: " + e.getMessage());
            }
        }
        return hashName;
    }

    public Boolean exists(String key) {
        if (jedisCluster != null) {
            try {
                return jedisCluster.exists(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to check existence of key in Redis: " + e.getMessage());
                return false;
            }
        } else {
            try (var jedis = jedisPool.getResource()) {
                return jedis.exists(key);
            } catch (Exception e) {
                rediVelocityLogger.sendErrorLogs("Failed to check existence of key in Redis: " + e.getMessage());
                return false;
            }
        }
    }
}
