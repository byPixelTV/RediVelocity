package de.bypixeltv.redivelocity.utils;

import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

@Singleton
public class ProxyIdGenerator {
    private final RedisController redisController;

    @Inject
    public ProxyIdGenerator(RedisController redisController) {
        this.redisController = redisController;
    }

    public String generate() {
        Set<String> proxiesList = redisController.getAllHashFields("rv-proxies");
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