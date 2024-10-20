package de.bypixeltv.redivelocity.managers;

import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.config.Config;
import lombok.Getter;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class RedisController extends BinaryJedisPubSub implements Runnable {

    private final RediVelocity rediVelocity;
    @Getter
    private final JedisPool jedisPool;
    private final byte[][] channelsInByte;
    private final AtomicBoolean isConnectionBroken = new AtomicBoolean(true);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    @Inject
    public RedisController(RediVelocity rediVelocity, Config config) {
        this.rediVelocity = rediVelocity;

        JedisPoolConfig jConfig = new JedisPoolConfig();
        int maxConnections = 10;

        jConfig.setMaxTotal(maxConnections);
        jConfig.setMaxIdle(maxConnections);
        jConfig.setMinIdle(1);
        jConfig.setBlockWhenExhausted(true);

        String password = config.getRedis().getPassword();
        if (password.isEmpty()) {
            this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort());
        } else {
            this.jedisPool = new JedisPool(jConfig, config.getRedis().getHost(), config.getRedis().getPort(), 2000, password);
        }

        this.channelsInByte = setupChannels();
    }

    @Override
    public void run() {
        if (!isConnectionBroken.get() || isConnecting.get()) {
            return;
        }
        rediVelocity.sendLogs("Connecting to Redis server...");
        isConnecting.set(true);
        try (var jedis = jedisPool.getResource()) {
            isConnectionBroken.set(false);
            rediVelocity.sendLogs("Connection to Redis server has established! Success!");
            jedis.subscribe(this, channelsInByte);
        } catch (Exception e) {
            isConnecting.set(false);
            isConnectionBroken.set(true);
            rediVelocity.sendErrorLogs("Connection to Redis server has failed! Please check your details in the configuration.");
            e.printStackTrace();
        }
    }

    public void shutdown() {
        jedisPool.close();
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

    public void sendMessage(String message, String channel) {
        try (var jedis = jedisPool.getResource()) {
            jedis.publish(channel, message);
        }
    }

    public void removeFromListByValue(String listName, String value) {
        try (var jedis = jedisPool.getResource()) {
            jedis.lrem(listName, 0, value);
        }
    }

    public void setHashField(String hashName, String fieldName, String value) {
        try (var jedis = jedisPool.getResource()) {
            String type = jedis.type(hashName);
            if (!"hash".equals(type)) {
                if ("none".equals(type)) {
                    jedis.hset(hashName, fieldName, value);
                } else {
                    System.err.println("Error: Key " + hashName + " doesn't hold a hash. It holds a " + type + ".");
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

    public void addToList(String listName, String[] values) {
        try (var jedis = jedisPool.getResource()) {
            for (String value : values) {
                jedis.rpush(listName, value);
            }
        }
    }

    public void setListValue(String listName, int index, String value) {
        try (var jedis = jedisPool.getResource()) {
            long listLength = jedis.llen(listName);
            if (index >= listLength) {
                System.err.println("Error: Index " + index + " does not exist in the list " + listName + ".");
            } else {
                jedis.lset(listName, index, value);
            }
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

    public void removeFromList(String listName, int index) {
        try (var jedis = jedisPool.getResource()) {
            long listLength = jedis.llen(listName);
            if (index >= listLength) {
                System.err.println("Error: Index " + index + " does not exist in the list " + listName + ".");
            } else {
                String tempKey = UUID.randomUUID().toString();
                jedis.lset(listName, index, tempKey);
                jedis.lrem(listName, 0, tempKey);
            }
        }
    }

    public void deleteList(String listName) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(listName);
        }
    }

    public void setString(String key, String value) {
        try (var jedis = jedisPool.getResource()) {
            jedis.set(key, value);
        }
    }

    public String getString(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.get(key);
        }
    }

    public void deleteString(String key) {
        try (var jedis = jedisPool.getResource()) {
            jedis.del(key);
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

    public List<String> getList(String listName) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.lrange(listName, 0, -1);
        }
    }

    public String getHashValueByField(String hashName, String fieldName) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.hget(hashName, fieldName);
        }
    }

    public Boolean exists(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.exists(key);
        }
    }

    public List<String> getHashFieldNamesByValue(String hashName, String value) {
        List<String> fieldNames = new ArrayList<>();
        try (var jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys(hashName);
            for (String key : keys) {
                Map<String, String> fieldsAndValues = jedis.hgetAll(key);
                for (Map.Entry<String, String> entry : fieldsAndValues.entrySet()) {
                    if (entry.getValue().equals(value)) {
                        fieldNames.add(entry.getKey());
                    }
                }
            }
        }
        return fieldNames;
    }

    private byte[][] setupChannels() {
        List<String> channels = Arrays.asList("global", "messaging", "friends", "utils", "other"); // replace with your actual channels
        byte[][] channelsInByte = new byte[channels.size()][];
        for (int i = 0; i < channels.size(); i++) {
            channelsInByte[i] = channels.get(i).getBytes(StandardCharsets.UTF_8);
        }
        return channelsInByte;
    }

}