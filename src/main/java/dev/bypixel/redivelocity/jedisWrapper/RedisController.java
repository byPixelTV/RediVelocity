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
import org.json.JSONObject;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class RedisController extends BinaryJedisPubSub implements Runnable {

    private final RediVelocityLogger rediVelocityLogger;
    @Getter
    private JedisPool jedisPool;
    private final AtomicBoolean isConnectionBroken = new AtomicBoolean(true);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    @Inject
    public RedisController(RediVelocityLogger rediVelocityLogger) { // Constructor directly injects RediVelocity
        this.rediVelocityLogger = rediVelocityLogger;

        ConfigLoader configLoader = new ConfigLoader(rediVelocityLogger);
        configLoader.load();

        Config config = configLoader.getConfig();

        JedisPoolConfig jConfig = new JedisPoolConfig();
        int maxConnections = 10;

        jConfig.setMaxTotal(maxConnections);
        jConfig.setMaxIdle(maxConnections);
        jConfig.setMinIdle(1);
        jConfig.setBlockWhenExhausted(true);

        // Initialize Redis connection
        try {
            String password = config.getRedis().getPassword();
            if (password.isEmpty()) {
                this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort());
            } else {
                this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort(), 2000, password);
            }
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("Failed to initialize RedisController: " + e.getMessage());
        }

        // Attempt to connect to Redis server
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
            try (var ignored = jedisPool.getResource()) {
                isConnectionBroken.set(false);
                rediVelocityLogger.sendConsoleMessage("<green>Successfully connected to Redis server.</green>");
            } catch (Exception e) {
                isConnecting.set(false);
                isConnectionBroken.set(true);
                rediVelocityLogger.sendErrorLogs("Connection to Redis server has failed: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        rediVelocityLogger.sendLogs("Shutting down Redis connection...");
        if (jedisPool != null) {
            jedisPool.close();
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

        try (var jedis = jedisPool.getResource()) {
            jedis.publish(channel, jsonString);
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("Failed to send post-login Redis message: " + e.getMessage());
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

        try (var jedis = jedisPool.getResource()) {
            jedis.publish(channel, jsonString);
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("Failed to send server switch Redis message: " + e.getMessage());
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

        try (var jedis = jedisPool.getResource()) {
            jedis.publish(channel, jsonString);
        }
    }

    public void setHashField(String hashName, String fieldName, String value) {
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

    public void deleteHashField(String hashName, String fieldName) {
        try (var jedis = jedisPool.getResource()) {
            jedis.hdel(hashName, fieldName);
        }
    }

    public void deleteHash(String hashName) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(hashName);
        }
    }

    public Map<String, String> getHashValuesAsPair(String hashName) {
        Map<String, String> values = new HashMap<>();
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.hkeys(hashName);
            for (String key : keys) {
                values.put(key, jedis.hget(hashName, key));
            }
        }
        return values;
    }

    public void setString(String key, String value) {
        try (var jedis = jedisPool.getResource()) {
            jedis.set(key, value);
        }
    }

    public void deleteString(String key) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(key);
        }
    }

    public void deleteHashFieldByBalue(String hashName, String value) {
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.hkeys(hashName);
            for (String key : keys) {
                if (jedis.hget(hashName, key).equals(value)) {
                    jedis.hdel(hashName, key);
                }
            }
        }
    }

    public String getString(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    public String getHashField(String hashName, String fieldName) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.hget(hashName, fieldName);
        }
    }

    public Set<String> getAllHashFields(String hashName) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.hkeys(hashName);
        }
    }

    public List<String> getAllHashValues(String hashName) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.hvals(hashName);
        }
    }

    public String getHashKeyByValue(String hashName, String value) {
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.hkeys(hashName);
            for (String key : keys) {
                if (jedis.hget(hashName, key).equals(value)) {
                    return key;
                }
            }
        }
        return null;
    }

    public Boolean exists(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        }
    }
}
