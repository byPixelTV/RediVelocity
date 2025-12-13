/*
 * Copyright (c) 2024-present byPixelTV & contributors.
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
import com.velocitypowered.api.event.player.ServerConnectedEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object ServerSwitchListener {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onServerSwitch(event: ServerConnectedEvent) {
        val player = event.player

        val previousServer =
            event.previousServer.map { server -> server.serverInfo.name }.orElse("null")
        val newServer = event.server

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            PlayercountUtil.setProxyPlayercount()
            RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
                put("action", "UPDATE")
            }, "redivelocity:global-player-updates")
            RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
                put("action", "SERVER_SWITCH")
                put("uuid", player.uniqueId.toString())
                put("username", player.username)
                put("ip", player.remoteAddress.toString().split(":")[0].substring(1))
                put("proxyId", RediVelocity.instance.proxyId)
                put("protocolVersion", player.protocolVersion.protocol)
                put("clientBrand", player.clientBrand)
                put("timestamp", System.currentTimeMillis())
                put("fromServer", previousServer)
                put("toServer", newServer.serverInfo.name)
            }, "redivelocity:players")

            RediVelocity.instance.lettuceClient.commands.hset("redivelocity:player:servers", player.uniqueId.toString(), newServer.serverInfo.name)
        }
    }
}