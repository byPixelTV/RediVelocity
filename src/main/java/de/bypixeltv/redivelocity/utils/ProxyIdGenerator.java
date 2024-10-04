package de.bypixeltv.redivelocity.utils;

import de.bypixeltv.redivelocity.RediVelocity;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;

@Singleton
public class ProxyIdGenerator {

    private final Provider<RediVelocity> rediVelocityProvider;

    @Inject
    public ProxyIdGenerator(Provider<RediVelocity> rediVelocityProvider) {
        this.rediVelocityProvider = rediVelocityProvider;
    }

    public String generate() {
        RediVelocity rediVelocity = rediVelocityProvider.get();
        JedisPool jedisPool = rediVelocity.getRedisController().getJedisPool();

        try (Jedis jedis = jedisPool.getResource()) {
            List<String> proxiesList = jedis.lrange("rv-proxies", 0, -1);
            List<Integer> ids = proxiesList.stream()
                    .map(proxy -> proxy.replace("Proxy-", ""))
                    .map(Integer::parseInt)
                    .sorted()
                    .toList();

            int newId = 1;
            for (int id : ids) {
                if (id == newId) {
                    newId++;
                } else {
                    break;
                }
            }
            return "Proxy-" + newId; // Return the counter as the new ID after the loop
        }
    }
}