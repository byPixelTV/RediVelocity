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
import com.velocitypowered.api.proxy.Player
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.antivpn.AntiVPNManager
import dev.bypixel.redivelocity.antivpn.IpManager
import dev.bypixel.redivelocity.antivpn.IpQueryUtil
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.bypixel.redivelocity.util.DiscordWebhookUtil
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
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player

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
                            delay(2000L.milliseconds) // Delay to ensure the player has fully logged in
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
            val ip = player.remoteAddress.toString().split(":")[0].substring(1)
            checkAntiVPN(player, ip)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun checkAntiVPN(player: Player, ip: String) {
        if (!player.hasPermission("redivelocity.admin.antivpn.bypass") && RediVelocity.instance.config.getBoolean(Route.fromString("anti-vpn.enabled"))) {
            val data = IpManager.cachePlayerIp(player.uniqueId.toString(), ip)
            RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                delay((2 * 50L).milliseconds) // Wait for 2 ticks to ensure the player is fully loaded

                val asnWhitelist = AntiVPNManager.getAllWhitelistedAsns()
                val ipWhitelist = AntiVPNManager.getAllWhitelistedIps()
                val asnBlacklist = AntiVPNManager.getAllBlacklistedAsns()
                val ipBlacklist = AntiVPNManager.getAllBlacklistedIps()

                if (ipBlacklist.contains(ip)) {
                    player.disconnect(
                        createBlacklistMessage(
                            "asn", ip, IpQueryUtil.getIpAsn(data), IpQueryUtil.getIpIsp(data),
                            IpQueryUtil.getFlaggedRisks(data)
                        )
                    )
                    return@launch
                }

                if (asnBlacklist.contains(IpQueryUtil.getIpAsn(data))) {
                    player.disconnect(
                        createBlacklistMessage(
                            "asn", ip, IpQueryUtil.getIpAsn(data), IpQueryUtil.getIpIsp(data),
                            IpQueryUtil.getFlaggedRisks(data)
                        )
                    )
                    return@launch
                }

                val cachedIpData = IpManager.getCachedIpDataByIp(ip) ?: return@launch
                val asn = IpQueryUtil.getIpAsn(cachedIpData)

                if (asnBlacklist.contains(asn)) {
                    player.disconnect(
                        createBlacklistMessage(
                            "asn", ip, asn, IpQueryUtil.getIpIsp(cachedIpData),
                            IpQueryUtil.getFlaggedRisks(cachedIpData)
                        )
                    )
                    return@launch
                }

                if (!asnWhitelist.contains(asn) && !ipWhitelist.contains(ip)) {
                    if (IpQueryUtil.isIpRisky(cachedIpData)) {
                        player.disconnect(
                            createBlacklistMessage(
                                "vpn", ip, asn, IpQueryUtil.getIpIsp(cachedIpData),
                                IpQueryUtil.getFlaggedRisks(cachedIpData)
                            )
                        )

                        val webhookUrl = RediVelocity.instance.config.getString(Route.fromString("anti-vpn.webhook"))
                        if (webhookUrl != null) {
                            sendWebhook(player, ip, asn, cachedIpData)
                        }
                    }
                }
            }
        }

        RediVelocity.instance.lettuceClient.withCoroutines {
            it.hset(
                "redivelocity:player:proxies", player.uniqueId.toString(),
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

    private fun createBlacklistMessage(type: String = "ip", ip: String, asn: String, isp: String, flags: List<String>): Component {
        return when (type) {
            "ip" -> RediVelocity.instance.messageConfig.getString(Route.fromString("antivpn_blocked_ip")) ?: "Your IP has been blocked."
            "asn" -> RediVelocity.instance.messageConfig.getString(Route.fromString("antivpn_blocked_asn")) ?: "Your ASN has been blocked."
            "vpn" -> RediVelocity.instance.messageConfig.getString(Route.fromString("antivpn_blocked_vpn")) ?: "VPNs are not allowed."
            else -> "Connection blocked."
        }.let { message ->
            MiniMessage.miniMessage().deserialize(message, Placeholder.unparsed(
                "ip", ip
            ), Placeholder.unparsed(
                "asn", asn
            ), Placeholder.unparsed(
                "isp", isp
            ), Placeholder.unparsed(
                "flags", flags.joinToString(", ")
            ))
        }
    }

    private fun sendWebhook(player: Player, ip: String, asn: String, cachedIpData: JSONObject) {
        val webhookUrl = RediVelocity.instance.config.getString(Route.fromString("anti-vpn.webhook"))
        if (webhookUrl != null) {
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
                .setThumbnailUrl("https://mineskin.eu/helm/${player.uniqueId}")
                .setColor("#ff0000")
                .build()

            if (RediVelocity.instance.config.getString(Route.fromString("anti-vpn.webhook")) != null || RediVelocity.instance.config.getString(Route.fromString("anti-vpn.webhook")) != "") {
                if (RediVelocity.instance.config.getBoolean(Route.fromString("anti-vpn.send-in-thread")) && RediVelocity.instance.config.getString(Route.fromString("anti-vpn.thread-id")) != null && RediVelocity.instance.config.getString(Route.fromString("anti-vpn.thread-id")) != "") {
                    DiscordWebhookUtil.sendEmbed(webhookUrl, embed, RediVelocity.instance.config.getLong(Route.fromString("anti-vpn.thread-id")))
                } else {
                    DiscordWebhookUtil.sendEmbed(webhookUrl, embed)
                }
            }
        }
    }
}