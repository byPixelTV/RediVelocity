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

package dev.bypixel.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.bypixel.redivelocity.RediVelocity;
import dev.bypixel.redivelocity.RediVelocityLogger;
import dev.bypixel.redivelocity.config.Config;
import dev.bypixel.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Singleton
public class DisconnectListener {

    private final Config config;
    private final RedisController redisController;
    private final String proxyId;
    private final RediVelocity rediVelocity;
    private final RediVelocityLogger logger;
    private final ProxyServer proxy;

    @Inject
    public DisconnectListener(Config config, RedisController redisController, RediVelocity rediVelocity, RediVelocityLogger logger, ProxyServer proxy) {
        this.config = config;
        this.redisController = redisController;
        this.proxyId = rediVelocity.getProxyId();
        this.rediVelocity = rediVelocity;
        this.logger = logger;
        this.proxy = proxy;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onDisconnectEvent(DisconnectEvent event) {
        var player = event.getPlayer();
        var redisConfig = config.getRedis();

        CompletableFuture.runAsync(() -> {
            redisController.sendJsonMessage(
                    "disconnect",
                    rediVelocity.getProxyId(),
                    player.getUsername(),
                    player.getUniqueId().toString(),
                    player.getClientBrand(),
                    player.getRemoteAddress().toString().split(":")[0].substring(1),
                    redisConfig.getChannel()
            );

            redisController.deleteHashField("rv-players-proxy", player.getUniqueId().toString());
            redisController.deleteHashField("rv-players-name", player.getUniqueId().toString());
            redisController.setHashField("rv-players-lastseen", player.getUniqueId().toString(), String.valueOf(System.currentTimeMillis()));
            redisController.deleteHashField("rv-players-server", player.getUniqueId().toString());

            redisController.setHashField("rv-proxy-players", proxyId, proxy.getAllPlayers().size() + "");

            Map<String, String> proxyPlayers = redisController.getHashValuesAsPair("rv-players-proxy");
            int values = proxyPlayers.values().stream()
                    .filter(value -> value.equals(proxyId))
                    .toList()
                    .size();
            redisController.setHashField("rv-proxy-players", proxyId, String.valueOf(values));

            Map<String, String> proxyPlayersMap = redisController.getHashValuesAsPair("rv-players-name");
            int sum = proxyPlayersMap.size();
            redisController.setString("rv-global-playercount", String.valueOf(sum));
        }).exceptionally(ex -> {
            logger.sendErrorLogs("Error while sending disconnect Redis message " + ex.getMessage());
            return null;
        });
    }
}