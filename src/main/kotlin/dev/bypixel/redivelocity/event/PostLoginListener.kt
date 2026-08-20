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

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.Player
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.antivpn.AntiVPNManager
import dev.bypixel.redivelocity.antivpn.IpManager
import dev.bypixel.redivelocity.antivpn.IpQueryUtil
import dev.bypixel.redivelocity.cache.PlayerSessionCache
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.bypixel.redivelocity.util.DiscordWebhookUtil
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.bypixel.redivelocity.util.UpdateUtil
import dev.bypixel.redivelocity.util.Version
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

object PostLoginListener {

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()
        val ip = player.remoteAddress.address.hostAddress

        val sessionId = PlayerSessionCache.create(player.uniqueId)

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            try {
                RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                    redis.hset(
                        "redivelocity:player:proxies",
                        uuid,
                        RediVelocity.instance.proxyId
                    )

                    redis.hset(
                        "redivelocity:player:sessions",
                        uuid,
                        sessionId
                    )

                    redis.hset(
                        "redivelocity:player:names",
                        uuid,
                        player.username
                    )

                    redis.hset(
                        "redivelocity:player:ips",
                        uuid,
                        ip
                    )
                }

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
                PlayerSessionCache.remove(player.uniqueId)

                RediVelocityLogger.error(
                    "Failed to register player ${player.username} ($uuid) in Redis: ${t.message}"
                )
            }
        }

        handleUpdateNotification(player)

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            checkAntiVPN(player, ip)
        }
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

    private suspend fun checkAntiVPN(
        player: Player,
        ip: String
    ) {
        if (
            player.hasPermission("redivelocity.admin.antivpn.bypass") ||
            !RediVelocity.instance.config.getBoolean(
                Route.fromString("anti-vpn.enabled")
            )
        ) {
            return
        }

        val data = IpManager.cachePlayerIp(
            player.uniqueId.toString(),
            ip
        )

        delay((2 * 50L).milliseconds)

        if (!player.isActive) {
            return
        }

        val asnWhitelist = AntiVPNManager.getAllWhitelistedAsns()
        val ipWhitelist = AntiVPNManager.getAllWhitelistedIps()
        val asnBlacklist = AntiVPNManager.getAllBlacklistedAsns()
        val ipBlacklist = AntiVPNManager.getAllBlacklistedIps()

        if (ipBlacklist.contains(ip)) {
            player.disconnect(
                createBlacklistMessage(
                    "ip",
                    ip,
                    IpQueryUtil.getIpAsn(data),
                    IpQueryUtil.getIpIsp(data),
                    IpQueryUtil.getFlaggedRisks(data)
                )
            )

            return
        }

        if (asnBlacklist.contains(IpQueryUtil.getIpAsn(data))) {
            player.disconnect(
                createBlacklistMessage(
                    "asn",
                    ip,
                    IpQueryUtil.getIpAsn(data),
                    IpQueryUtil.getIpIsp(data),
                    IpQueryUtil.getFlaggedRisks(data)
                )
            )

            return
        }

        val cachedIpData = IpManager.getCachedIpDataByIp(ip)
            ?: return

        val asn = IpQueryUtil.getIpAsn(cachedIpData)

        if (asnBlacklist.contains(asn)) {
            player.disconnect(
                createBlacklistMessage(
                    "asn",
                    ip,
                    asn,
                    IpQueryUtil.getIpIsp(cachedIpData),
                    IpQueryUtil.getFlaggedRisks(cachedIpData)
                )
            )

            return
        }

        if (
            !asnWhitelist.contains(asn) &&
            !ipWhitelist.contains(ip) &&
            IpQueryUtil.isIpRisky(cachedIpData)
        ) {
            player.disconnect(
                createBlacklistMessage(
                    "vpn",
                    ip,
                    asn,
                    IpQueryUtil.getIpIsp(cachedIpData),
                    IpQueryUtil.getFlaggedRisks(cachedIpData)
                )
            )

            val webhookUrl = RediVelocity.instance.config.getString(
                Route.fromString("anti-vpn.webhook")
            )

            if (!webhookUrl.isNullOrBlank()) {
                sendWebhook(
                    player,
                    ip,
                    asn,
                    cachedIpData
                )
            }
        }
    }

    private fun createBlacklistMessage(
        type: String = "ip",
        ip: String,
        asn: String,
        isp: String,
        flags: List<String>
    ): Component {
        val message = when (type) {
            "ip" -> {
                RediVelocity.instance.messageConfig.getString(
                    Route.fromString("antivpn_blocked_ip")
                ) ?: "Your IP has been blocked."
            }

            "asn" -> {
                RediVelocity.instance.messageConfig.getString(
                    Route.fromString("antivpn_blocked_asn")
                ) ?: "Your ASN has been blocked."
            }

            "vpn" -> {
                RediVelocity.instance.messageConfig.getString(
                    Route.fromString("antivpn_blocked_vpn")
                ) ?: "VPNs are not allowed."
            }

            else -> {
                "Connection blocked."
            }
        }

        return MiniMessage.miniMessage().deserialize(
            message,
            Placeholder.unparsed(
                "ip",
                ip
            ),
            Placeholder.unparsed(
                "asn",
                asn
            ),
            Placeholder.unparsed(
                "isp",
                isp
            ),
            Placeholder.unparsed(
                "flags",
                flags.joinToString(", ")
            )
        )
    }

    private fun sendWebhook(
        player: Player,
        ip: String,
        asn: String,
        cachedIpData: JSONObject
    ) {
        val webhookUrl = RediVelocity.instance.config.getString(
            Route.fromString("anti-vpn.webhook")
        )

        if (webhookUrl.isNullOrBlank()) {
            return
        }

        val embed = DiscordWebhookUtil.EmbedBuilder()
            .setTitle("RediVelocity AntiVPN")
            .setDescription(
                """
                **Name:** ${player.username}
                **UUID:** ${player.uniqueId}
                **IP:** $ip
                **ASN:** $asn
                **ISP:** ${IpQueryUtil.getIpIsp(cachedIpData)}
                **Flagged Risks:**
                - ${IpQueryUtil.getFlaggedRisks(cachedIpData).joinToString("\n- ")}
                """.trimIndent()
            )
            .setTimestamp()
            .setThumbnailUrl(
                "https://mineskin.eu/helm/${player.uniqueId}"
            )
            .setColor("#ff0000")
            .build()

        val sendInThread = RediVelocity.instance.config.getBoolean(
            Route.fromString("anti-vpn.send-in-thread")
        )

        val threadId = RediVelocity.instance.config.getString(
            Route.fromString("anti-vpn.thread-id")
        )

        if (sendInThread && !threadId.isNullOrBlank()) {
            DiscordWebhookUtil.sendEmbed(
                webhookUrl,
                embed,
                threadId.toLong()
            )
        } else {
            DiscordWebhookUtil.sendEmbed(
                webhookUrl,
                embed
            )
        }
    }
}