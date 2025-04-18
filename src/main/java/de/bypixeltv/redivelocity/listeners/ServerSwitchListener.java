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

package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import java.util.concurrent.CompletableFuture;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class ServerSwitchListener {

    private final RediVelocity rediVelocity;
    private final Config config;
    private final RedisController redisController;
    private final ExecutorService redisExecutor = Executors.newFixedThreadPool(5);

    @Inject
    public ServerSwitchListener(RediVelocity rediVelocity, Config config, RedisController redisController) {
        this.rediVelocity = rediVelocity;
        this.config = config;
        this.redisController = redisController;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        var player = event.getPlayer();
        var previousServerName = event.getPreviousServer().map(server -> server.getServerInfo().getName()).orElse("null");
        var redisConfig = config.getRedis();

        CompletableFuture.runAsync(() -> {
            redisController.sendServerSwitchMessage(
                    "serverSwitch",
                    rediVelocity.getProxyId(),
                    player.getUsername(),
                    player.getUniqueId().toString(),
                    player.getClientBrand(),
                    player.getRemoteAddress().toString().split(":")[0].substring(1),
                    event.getServer().getServerInfo().getName() != null ? event.getServer().getServerInfo().getName() : "null",
                    previousServerName,
                    redisConfig.getChannel()
            );

            redisController.setHashField("rv-players-server", player.getUniqueId().toString(), event.getServer().getServerInfo().getName());
        }, redisExecutor).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }
}