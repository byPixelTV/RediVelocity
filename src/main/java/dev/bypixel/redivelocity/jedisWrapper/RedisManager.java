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
import jakarta.inject.Inject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

import java.util.List;

public class RedisManager {

    private final JedisPool jedisPool;
    private final RediVelocityLogger rediVelocityLogger;

    @Inject
    public RedisManager(RediVelocityLogger rediVelocityLogger, JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.rediVelocityLogger = rediVelocityLogger;
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
                rediVelocityLogger.sendErrorLogs(e.getMessage());
            }
        }).start();
    }

    @FunctionalInterface
    public interface RedisMessageListener {
        void onMessage(String pattern, String channel, String message);
    }
}
