package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.server.ServerPing
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import dev.bypixel.redivelocity.model.MotdEntry
import dev.bypixel.redivelocity.util.SerializationHelpers
import dev.dejvokep.boostedyaml.route.Route
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.*

object ProxyPingListener {
    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val config = RediVelocity.instance.config
        val rawConfig = RediVelocity.instance.rawConfig

        val maxPlayerCount = if (
            config.getBoolean(Route.fromString("login-configuration.enabled"))
        ) {
            RediVelocity.instance.maxPlayers
        } else {
            null
        }

        var motd: String? = null
        var protocolText: String? = null
        var playerInfo: String? = null

        if (
            config.getBoolean(Route.fromString("login-configuration.enabled")) &&
            config.getBoolean(Route.fromString("login-configuration.use-motd"))
        ) {
            val motdsSection = rawConfig.getSection(
                Route.fromString("login-configuration.motds")
            )

            val motds = motdsSection
                ?.getRoutesAsStrings(false)
                ?.mapNotNull { id ->
                    val basePath = "login-configuration.motds.$id"

                    val content = rawConfig.getString(
                        Route.fromString("$basePath.content")
                    ) ?: return@mapNotNull null

                    MotdEntry(
                        id = id,
                        content = content,
                        playerInfo = rawConfig.getString(
                            Route.fromString("$basePath.playerInfo")
                        ),
                        protocolText = rawConfig.getString(
                            Route.fromString("$basePath.protocolText")
                        ),
                        maintenance = rawConfig.getBoolean(
                            Route.fromString("$basePath.maintenance")
                        )
                    )
                }
                ?: emptyList()

            val normalMotds = motds.filter {
                !it.maintenance
            }

            val maintenanceMotds = motds.filter {
                it.maintenance
            }

            val selectedMotd: MotdEntry? =
                if (RediVelocity.instance.maintenance) {
                    if (RediVelocity.instance.forceMaintenanceMotd.isNotBlank()) {
                        maintenanceMotds.firstOrNull {
                            it.id == RediVelocity.instance.forceMaintenanceMotd
                        }
                    } else {
                        maintenanceMotds.randomOrNull()
                    }
                } else {
                    if (RediVelocity.instance.forceMotd.isNotBlank()) {
                        normalMotds.firstOrNull {
                            it.id == RediVelocity.instance.forceMotd
                        }
                    } else {
                        normalMotds.randomOrNull()
                    }
                }

            if (selectedMotd != null) {
                motd = SerializationHelpers.convertToMinimessage(
                    selectedMotd.content
                )

                protocolText = selectedMotd.protocolText
                    ?.takeIf { it.isNotBlank() }

                playerInfo = selectedMotd.playerInfo
                    ?.takeIf { it.isNotBlank() }
            }
        }

        val playerCount: Int

        if (
            config.getBoolean(
                Route.fromString("playercount-sync.use-backend-server-count")
            )
        ) {
            val includedBackendServers = config.getStringList(
                Route.fromString("playercount-sync.backend-server-names")
            )

            val allServers = RediVelocity.instance.proxy.allServers

            var totalPlayers = 0

            val patterns = includedBackendServers.mapNotNull {
                runCatching {
                    it.toRegex()
                }.getOrNull()
            }

            val globPatterns = includedBackendServers.map {
                Regex(
                    "^" +
                            Regex.escape(it)
                                .replace("\\*", ".*") +
                            "$"
                )
            }

            allServers.forEach { server ->
                val name = server.serverInfo.name

                val matches =
                    patterns.any { it.matches(name) } ||
                            globPatterns.any { it.matches(name) }

                if (matches) {
                    totalPlayers += server.playersConnected.size
                }
            }

            playerCount = totalPlayers
        } else {
            playerCount = PlayercountUtil.globalPlayercountCache.toInt()
        }

        val ping = event.ping.asBuilder()

        ping.onlinePlayers(playerCount)

        if (maxPlayerCount != null) {
            ping.maximumPlayers(maxPlayerCount)
        }

        if (motd != null) {
            ping.description(
                MiniMessage.miniMessage().deserialize(
                    motd
                        .replace(
                            "%player_count%",
                            playerCount.toString()
                        )
                        .replace(
                            "%max_players%",
                            maxPlayerCount?.toString() ?: "Unknown"
                        )
                        .replace(
                            "%proxy_id%",
                            RediVelocity.instance.proxyId
                        )
                )
            )
        }

        if (protocolText != null) {
            val protocolComponent = MiniMessage.miniMessage().deserialize(
                protocolText
                    .replace(
                        "%player_count%",
                        playerCount.toString()
                    )
                    .replace(
                        "%max_players%",
                        maxPlayerCount?.toString() ?: "Unknown"
                    )
                    .replace(
                        "%proxy_id%",
                        RediVelocity.instance.proxyId
                    )
            )

            val legacyProtocol =
                SerializationHelpers.convertToLegacyParagraphs(
                    protocolComponent
                )

            ping.version(
                ServerPing.Version(
                    if (RediVelocity.instance.maintenance) {
                        1
                    } else {
                        event.ping.version.protocol
                    },
                    legacyProtocol
                )
            )
        }

        if (playerInfo != null) {
            val infoComponent = MiniMessage.miniMessage().deserialize(
                playerInfo
                    .replace(
                        "%player_count%",
                        playerCount.toString()
                    )
                    .replace(
                        "%max_players%",
                        maxPlayerCount?.toString() ?: "Unknown"
                    )
                    .replace(
                        "%proxy_id%",
                        RediVelocity.instance.proxyId
                    )
            )

            val infoString =
                SerializationHelpers.convertToLegacyParagraphs(
                    infoComponent
                )

            val samplePlayers = infoString
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map {
                    ServerPing.SamplePlayer(
                        it,
                        UUID.randomUUID()
                    )
                }

            ping.samplePlayers(samplePlayers)
        }

        event.ping = ping.build()
    }
}