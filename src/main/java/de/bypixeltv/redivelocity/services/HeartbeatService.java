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

import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    private final RedisController redisController;
    private final String proxyId;
    private final RediVelocityLogger logger;
    private final RediVelocity rediVelocity;

    @Inject
    public HeartbeatService(RedisController redisController, String proxyId, RediVelocityLogger logger, RediVelocity rediVelocity) {
        this.redisController = redisController;
        this.proxyId = proxyId;
        this.logger = logger;
        this.rediVelocity = rediVelocity;
    }

    public void startHeartbeat() {
        rediVelocity.proxy.getScheduler().buildTask(rediVelocity, () -> {
            try {
                redisController.setHashField("rv-proxy-heartbeat", proxyId, System.currentTimeMillis() + "");
            } catch (Exception e) {
                logger.sendErrorLogs("Error while sending heartbeat " + e.getMessage());
            }
        }).repeat(10, TimeUnit.SECONDS).schedule();
    }
}