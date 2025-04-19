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

import com.velocitypowered.api.proxy.ProxyServer;
import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.RediVelocityLogger;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlayerCalcService {

    private final RedisController redisController;
    private final String proxyId;
    private final RediVelocityLogger logger;
    private final RediVelocity rediVelocity;
    private final ExecutorService redisExecutor = Executors.newFixedThreadPool(5);
    private final ProxyServer proxy;

    @Inject
    public PlayerCalcService(RedisController redisController, String proxyId, RediVelocityLogger logger, RediVelocity rediVelocity, ProxyServer proxy) {
        this.redisController = redisController;
        this.proxyId = proxyId;
        this.logger = logger;
        this.rediVelocity = rediVelocity;
        this.proxy = proxy;
    }

    public void startCalc() {
        rediVelocity.proxy.getScheduler().buildTask(rediVelocity, () -> {
            redisExecutor.submit(() -> {
                try {
                    redisController.setHashField("rv-proxy-players", proxyId, proxy.getAllPlayers().size() + "");
                } catch (Exception e) {
                    logger.sendErrorLogs("Error while calculating player count " + e.getMessage());
                }
            });
        }).repeat(2, TimeUnit.SECONDS).schedule();
    }
}