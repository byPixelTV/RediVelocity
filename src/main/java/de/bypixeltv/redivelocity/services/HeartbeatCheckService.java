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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HeartbeatCheckService {

    private static final String RV_PROXY_HEARTBEAT = "rv-proxy-heartbeat";
    private static final String RV_PROXIES = "rv-proxies";
    private static final String RV_PROXY_LEADER = "rv-proxy-leader";

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
                String leaderProxy = redisController.getString(RV_PROXY_LEADER);

                if (leaderProxy == null || leaderProxy.isEmpty()) {
                    Set<String> activeProxies = redisController.getAllHashFields(RV_PROXIES);
                    if (!activeProxies.isEmpty()) {
                        List<String> proxyList = new ArrayList<>(activeProxies);
                        String newLeader = proxyList.get(new SecureRandom().nextInt(proxyList.size()));
                        redisController.setString(RV_PROXY_LEADER, newLeader);
                        logger.sendLogs("No leader found. New leader: " + newLeader);
                        leaderProxy = newLeader;
                    }
                }

                final String finalLeaderProxy = leaderProxy;
                if (proxyId.equals(finalLeaderProxy)) {
                    redisController.getAllHashFields(RV_PROXY_HEARTBEAT).forEach(proxy -> {
                        String heartbeatValue = redisController.getHashField(RV_PROXY_HEARTBEAT, proxy);
                        if (heartbeatValue != null) {
                            long timestamp = Long.parseLong(heartbeatValue);
                            long currentTimestamp = System.currentTimeMillis();
                            boolean isOlder = (currentTimestamp - timestamp) > 10000;
                            if (isOlder) {
                                logger.sendLogs("Proxy " + proxy + " is inactive for more than 10 seconds. Removing from Redis.");
                                redisController.deleteHashField(RV_PROXY_HEARTBEAT, proxy);
                            }
                        }
                    });

                    Set<String> proxyNameList = redisController.getAllHashFields(RV_PROXIES);
                    Set<String> activeProxies = redisController.getAllHashFields(RV_PROXY_HEARTBEAT);

                    List<String> missingValues = new ArrayList<>(proxyNameList);
                    missingValues.removeAll(activeProxies);

                    missingValues.forEach(proxy -> {
                        logger.sendErrorLogs("Proxy " + proxy + " is not responding. Removing from Redis.");
                        redisController.deleteHashField("rv-proxy-players", proxy);
                        redisController.deleteHashField(RV_PROXY_HEARTBEAT, proxy);
                        redisController.deleteHashField(RV_PROXIES, proxy);

                        if (proxy.equals(finalLeaderProxy)) {
                            Set<String> remainingProxies = redisController.getAllHashFields(RV_PROXIES);
                            if (!remainingProxies.isEmpty()) {
                                List<String> proxyList = new ArrayList<>(remainingProxies);
                                String newLeader = proxyList.get(new SecureRandom().nextInt(proxyList.size()));
                                redisController.setString(RV_PROXY_LEADER, newLeader);
                                logger.sendLogs("New leader proxy: " + newLeader);
                            }
                        }

                        redisController.deleteHashFieldByBalue("rv-players-proxy", proxy);
                    });
                }
            } catch (Exception e) {
                logger.sendErrorLogs("Error during heartbeat check: " + e.getMessage());
            }
        }).repeat(15, TimeUnit.SECONDS).schedule();
    }
}