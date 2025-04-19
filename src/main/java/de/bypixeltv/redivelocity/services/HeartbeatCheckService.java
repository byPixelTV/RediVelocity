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
package de.bypixeltv.redivelocity.services;

import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.RediVelocityLogger;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HeartbeatCheckService {

    private final RedisController redisController;
    private final String proxyId;
    private final RediVelocityLogger logger;
    private final RediVelocity rediVelocity;

    @Inject
    public HeartbeatCheckService(RedisController redisController, String proxyId, RediVelocityLogger logger, RediVelocity rediVelocity) {
        this.redisController = redisController;
        this.proxyId = proxyId;
        this.logger = logger;
        this.rediVelocity = rediVelocity;
    }

    public void startHeartbeatCheck() {
        rediVelocity.proxy.getScheduler().buildTask(rediVelocity, () -> {
            try {
                ArrayList<Integer> proxiesList = new java.util.ArrayList<>(List.of());
                redisController.getAllHashFields("rv-proxies").forEach(proxy -> {
                    proxiesList.add(Integer.parseInt(proxy.replace("Proxy-", "")));
                });
                if (Collections.min(proxiesList) == Integer.parseInt(proxyId.split("-")[1])) {
                    redisController.getAllHashFields("rv-proxy-heartbeat").forEach(proxy -> {
                        long timestamp = Long.parseLong(redisController.getHashField("rv-proxy-heartbeat", proxy));
                        long currentTimestamp = System.currentTimeMillis() / 1000;
                        boolean isOlder = (currentTimestamp - timestamp) > 10;
                        if (isOlder) {
                            redisController.deleteHashField("rv-proxy-heartbeat", proxy);
                        }
                    });

                    Set<String> proxyNameList = redisController.getAllHashFields("rv-proxies");
                    Set<String> activeProxies = redisController.getAllHashFields("rv-proxy-heartbeat");

                    List<String> missingValues = new ArrayList<>(proxyNameList);
                    missingValues.removeAll(activeProxies);

                    missingValues.forEach(proxy -> {
                        logger.sendErrorLogs("Proxy " + proxy + " is not responding. Removing from Redis.");
                        redisController.deleteHashField("rv-proxy-players", proxy);
                        redisController.deleteHashKeyByValue("rv-proxy-heartbeat", proxy);
                        redisController.deleteHashKeyByValue("rv-players-proxy", proxy);
                        redisController.deleteHashKeyByValue("rv-proxies", proxy);
                    });
                }
            } catch (Exception e) {
                logger.sendErrorLogs("Error while sending heartbeat " + e.getMessage());
            }
        }).repeat(15, TimeUnit.SECONDS).schedule();
    }
}