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

package dev.bypixel.redivelocity.services;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.bypixel.redivelocity.RediVelocity;
import dev.bypixel.redivelocity.RediVelocityLogger;
import dev.bypixel.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {
    private final RedisController redisController;
    private final String proxyId;
    private final RediVelocityLogger logger;
    private final ProxyServer proxy;
    private final RediVelocity rediVelocity;
    private final boolean debugMode;

    @Inject
    public HeartbeatService(RedisController redisController, String proxyId, RediVelocityLogger logger, RediVelocity rediVelocity, ProxyServer proxy, boolean debugMode) {
        this.redisController = redisController;
        this.proxyId = proxyId;
        this.logger = logger;
        this.proxy = proxy;
        this.rediVelocity = rediVelocity;
        this.debugMode = debugMode;
    }

    public void startHeartbeatService() {
        proxy.getScheduler().buildTask(rediVelocity, () -> {
            redisController.setHashField("rv-proxy-heartbeat", proxyId, String.valueOf(System.currentTimeMillis()));

            String currentLeader = redisController.getString("rv-proxy-leader");
            if (proxyId.equals(currentLeader)) {
                Set<String> registeredProxies = redisController.getAllHashFields("rv-proxies");

                for (String proxy : registeredProxies) {
                    String lastHeartbeat = redisController.getHashField("rv-proxy-heartbeat", proxy);

                    if (lastHeartbeat == null) {
                        cleanupDeadProxy(proxy);
                        continue;
                    }

                    long lastBeat = Long.parseLong(lastHeartbeat);
                    if (System.currentTimeMillis() - lastBeat > 30000) {
                        logger.sendLogs("Proxy " + proxy + " did not send a heartbeat for 30 seconds. Cleaning up...");
                        cleanupDeadProxy(proxy);
                    }
                }
            }
        }).repeat(10, TimeUnit.SECONDS).schedule();
    }

    private void cleanupDeadProxy(String deadProxyId) {
        logger.sendLogs("Removing dead proxy: " + deadProxyId);

        redisController.deleteHashField("rv-proxies", deadProxyId);
        redisController.deleteHashField("rv-proxy-players", deadProxyId);
        redisController.deleteHashField("rv-proxy-heartbeat", deadProxyId);
        redisController.deleteHashField("rv-proxy-votes", deadProxyId);
        redisController.deleteHashFieldByBalue("rv-players-proxy", deadProxyId);

        String currentLeader = redisController.getString("rv-proxy-leader");
        if (deadProxyId.equals(currentLeader)) {
            Set<String> remainingProxies = redisController.getAllHashFields("rv-proxies");
            if (!remainingProxies.isEmpty()) {
                List<String> proxyList = new ArrayList<>(remainingProxies);
                String newLeader = proxyList.get(new SecureRandom().nextInt(proxyList.size()));
                redisController.setString("rv-proxy-leader", newLeader);
                if (debugMode) {
                    logger.sendLogs("New leader after proxy crash: " + newLeader);
                }
            } else {
                redisController.deleteString("rv-proxy-leader");
            }
        }
    }
}
