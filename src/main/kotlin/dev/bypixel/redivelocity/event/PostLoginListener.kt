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

package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import org.json.JSONObject

object PostLoginListener {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player

        if (RediVelocity.instance.config.getBoolean(Route.fromString("playerversion-check.enabled"))) {
            val allowedVersions = RediVelocity.instance.config.getIntList(Route.fromString("playerversion-check.allowed-versions"))

            if (!allowedVersions.contains(player.protocolVersion.protocol) && !player.hasPermission("redivelocity.admin.versionbypass")) {
                player.disconnect(MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString("playerversion_unsupported"))))
                return
            }
        }

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            RediVelocity.instance.lettuceClient.commands.hset(
                "redivelocity:proxy:players", player.uniqueId.toString(),
                RediVelocity.instance.proxyId
            )

            RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
                put("action", "POST_LOGIN")
                put("uuid", player.uniqueId.toString())
                put("username", player.username)
                put("ip", player.remoteAddress.toString().split(":")[0].substring(1))
                put("proxyId", RediVelocity.instance.proxyId)
                put("protocolVersion", player.protocolVersion.protocol)
                put("clientBrand", player.clientBrand)
                put("timestamp", System.currentTimeMillis())
            }, "redivelocity:players")

            RediVelocity.instance.lettuceClient.commands.hset("redivelocity:player:ips", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
            RediVelocity.instance.lettuceClient.commands.hset("redivelocity:player:names", player.uniqueId.toString(), player.username)
        }
    }
}