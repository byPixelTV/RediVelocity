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
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.dejvokep.boostedyaml.route.Route

object ProxyPingListener {
    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        if (RediVelocity.instance.config.getBoolean(Route.fromString("playercount-sync.use-backend-server-count"))) {
            val includedBackendServers = RediVelocity.instance.config.getStringList(
                Route.fromString("playercount-sync.backend-server-names")
            )

            val allServers = RediVelocity.instance.proxy.allServers

            var totalPlayers = 0

            val patterns = includedBackendServers.map { it.toRegex() }
            val globPatterns = includedBackendServers.map {
                Regex("^" + Regex.escape(it).replace("\\*", ".*") + "$")
            }

            allServers.forEach { server ->
                val name = server.serverInfo.name

                if (patterns.any { it.matches(name) }) {
                    totalPlayers += server.playersConnected.size
                }

                if (globPatterns.any { it.matches(name) }) { totalPlayers += server.playersConnected.size }
            }

            val ping = event.ping.asBuilder()

            ping.onlinePlayers(totalPlayers)

            event.ping = ping.build()
        } else {
            val ping = event.ping.asBuilder()

            ping.onlinePlayers(PlayercountUtil.globalPlayercountCache.toInt())

            event.ping = ping.build()
        }
    }
}