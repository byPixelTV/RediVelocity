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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.Player
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.cache.PlayerSessionCache
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.bypixel.redivelocity.redis.RedisPlayerState
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.bypixel.redivelocity.util.UpdateUtil
import dev.bypixel.redivelocity.util.Version
import dev.dejvokep.boostedyaml.route.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

object PostLoginListener {

    @Subscribe
    fun onPostLogin(event: PostLoginEvent): EventTask {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val ip = player.remoteAddress.address.hostAddress

        val sessionId = PlayerSessionCache.create(player)

        val registration = EventTask.async {
            runBlocking {
                try {
                    val registered = PlayerSessionCache.withLock(player.uniqueId) {
                        if (RediVelocity.instance.stopping || !player.isActive || PlayerSessionCache.get(player) != sessionId) {
                            false
                        } else {
                            RedisPlayerState.update("login", uuid, sessionId, player.username, ip)
                        }
                    }
                    if (!registered) return@runBlocking

                    RediVelocity.instance.lettuceClient.sendMessage(
                        JSONObject().apply {
                            put("action", "POST_LOGIN")
                            put("uuid", uuid)
                            put("username", player.username)
                            put("ip", ip)
                            put("proxyId", RediVelocity.instance.proxyId)
                            put("sessionId", sessionId)
                            put("protocolVersion", player.protocolVersion.protocol)
                            put("clientBrand", player.clientBrand)
                            put("timestamp", System.currentTimeMillis())
                        },
                        "redivelocity:players"
                    )

                    PlayercountUtil.setProxyPlayercount()
                    PlayercountUtil.calcGlobalPlayercount()

                    RediVelocity.instance.lettuceClient.sendMessage(
                        JSONObject().apply {
                            put("action", "UPDATE")
                        },
                        "redivelocity:global-player-updates"
                    )
                } catch (t: Throwable) {
                    // Retain the token: Redis may have committed before a timeout or notification failure.
                    // Disconnect can still remove that exact session.
                    RediVelocityLogger.error(
                        "Failed to register player ${player.username} ($uuid) in Redis: ${t.message}"
                    )
                }
            }
        }

        handleUpdateNotification(player)

        return registration
    }

    private fun handleUpdateNotification(player: Player) {
        if (
            !RediVelocity.instance.config.getBoolean(
                Route.fromString("update-check.enabled")
            )
        ) {
            return
        }

        if (
            !RediVelocity.instance.config.getBoolean(
                Route.fromString("update-check.notify-admins")
            )
        ) {
            return
        }

        if (!player.hasPermission("redivelocity.admin.updatecheck")) {
            return
        }

        val cachedVersion = UpdateUtil.getLatestCachedVersion() ?: return

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val currentVersionString = RediVelocity.server.pluginManager
                .getPlugin("redivelocity")
                .get()
                .description
                .version
                .orElse("0.0.0")

            if (currentVersionString.contains("+")) {
                return@launch
            }

            val latestVersion = Version.fromString(cachedVersion)
            val currentVersion = Version.fromString(currentVersionString)

            if (latestVersion.compareTo(currentVersion) <= 0) {
                return@launch
            }

            delay(2000L.milliseconds)

            if (!player.isActive) {
                return@launch
            }

            player.sendMessage(
                MiniMessage.miniMessage().deserialize(
                    "<prefix> An <#08a8f8>update</#08a8f8> is available! " +
                            "You are running version <#dc2626><current_version></#dc2626>, " +
                            "latest version is <#4bfb00><latest_version></#4bfb00>. " +
                            "Download it on " +
                            "<click:open_url:'https://www.github.com/byPixelTV/RediVelocity/releases'>" +
                            "<u><#08a8f8>GitHub (click)</#08a8f8></u></click>.",
                    Placeholder.unparsed(
                        "current_version",
                        currentVersionString
                    ),
                    Placeholder.unparsed(
                        "latest_version",
                        cachedVersion
                    ),
                    Placeholder.parsed(
                        "prefix",
                        RediVelocity.instance.messageConfig.getString(
                            Route.fromString("prefix")
                        )
                    )
                )
            )
        }
    }
}
