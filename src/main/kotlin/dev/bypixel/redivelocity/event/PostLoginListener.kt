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
import com.velocitypowered.api.event.connection.PostLoginEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.bypixel.redivelocity.util.UpdateUtil
import dev.bypixel.redivelocity.util.Version
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
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

        if (RediVelocity.instance.config.getBoolean(Route.fromString("update-check.enabled")) && RediVelocity.instance.config.getBoolean(Route.fromString("update-check.notify-admins"))) {
            if (player.hasPermission("redivelocity.admin.updatecheck")) {
                val cachedVersion = UpdateUtil.getLatestCachedVersion()
                if (cachedVersion != null) {
                    RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                        val currentVersionString = RediVelocity.server.pluginManager.getPlugin("redivelocity").get().description.version.orElse("0.0.0")
                        val latestVersion = Version.fromString(cachedVersion)
                        val currentVersion = Version.fromString(currentVersionString)
                        val compare = latestVersion.compareTo(currentVersion)

                        if (currentVersionString.contains("+")) {
                            return@launch
                        }

                        if (compare > 0) {
                            delay(2000L) // Delay to ensure the player has fully logged in
                            player.sendMessage(
                                MiniMessage.miniMessage().deserialize(
                                    "<prefix> An <#08a8f8>update</#08a8f8> is available! You are running version <#dc2626><current_version></#dc2626>, latest version is <#4bfb00><latest_version></#4bfb00>. Download it on <click:open_url:'https://www.github.com/byPixelTV/RediVelocity/releases'><u><#08a8f8>GitHub (click)</#08a8f8></u></click>.",
                                    Placeholder.unparsed("current_version", currentVersionString),
                                    Placeholder.unparsed("latest_version", cachedVersion),
                                    Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                                )
                            )
                        }
                    }
                }
            }
        }

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hset(
                    "redivelocity:proxy:players", player.uniqueId.toString(),
                    RediVelocity.instance.proxyId
                )
            }

            RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
                put("action", "UPDATE")
            }, "redivelocity:global-player-updates")

            PlayercountUtil.setProxyPlayercount()
            PlayercountUtil.calcGlobalPlayercount()

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

            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hset("redivelocity:player:ips", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
            }
            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hset("redivelocity:player:names", player.uniqueId.toString(), player.username)
            }
        }
    }
}