package de.bypixeltv.redivelocity.jedisWrapper;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.List;

public class RedisManager {

    private final JedisPool jedisPool;

    public RedisManager(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public void subscribe(List<String> channels, RedisMessageListener onMessage) {
        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onPMessage(String pattern, String channel, String message) {
                onMessage.onMessage(pattern, channel, message);
            }
        };

        new Thread(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.psubscribe(jedisPubSub, channels.toArray(new String[0]));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FunctionalInterface
    public interface RedisMessageListener {
        void onMessage(String pattern, String channel, String message);
    }
}
