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

package dev.bypixel.redivelocity.pubsub;

import com.velocitypowered.api.proxy.ProxyServer;
import dev.bypixel.redivelocity.jedisWrapper.RedisManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

@Singleton
public class MessageListener {

    private static final List<String> channels = List.of("redivelocity-kick", "redivelocity-heartbeat");

    private final RedisManager redisManager;
    private final ProxyServer proxyServer;

    @Inject
    public MessageListener(RedisManager redisManager, ProxyServer proxyServer) {
        this.redisManager = redisManager;
        this.proxyServer = proxyServer;
        init();
    }

    private void init() {
        redisManager.subscribe(channels, (event, channel, msg) -> {
            if ("redivelocity-kick".equals(channel)) {
                JSONObject message = new JSONObject(msg);
                String messagesString = message.getString("messages");
                JSONObject data = new JSONObject(messagesString);
                if (data.has("uuid") && data.has("reason")) {
                    UUID uuid = UUID.fromString(data.getString("uuid"));
                    String reason = data.getString("reason");

                    proxyServer.getPlayer(uuid).ifPresent(player -> player.disconnect(MiniMessage.miniMessage().deserialize(reason)));
                }
            }
        });
    }
}