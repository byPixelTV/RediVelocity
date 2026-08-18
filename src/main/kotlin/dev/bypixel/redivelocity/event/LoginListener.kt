package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.dejvokep.boostedyaml.route.Route
import net.kyori.adventure.text.minimessage.MiniMessage

object LoginListener {

    @Subscribe
    fun onLogin(event: LoginEvent) {
        val player = event.player

        if (!RediVelocity.instance.config.getBoolean(
                Route.fromString("login-configuration.enabled")
            )
        ) {
            return
        }

        if (
            RediVelocity.instance.config.getBoolean(
                Route.fromString("login-configuration.enforce-max-players")
            )
        ) {
            val bypassPermission = RediVelocity.instance.config.getString(
                Route.fromString("login-configuration.max-players-bypass-permission")
            )

            val hasBypass = player.hasPermission(bypassPermission)

            if (!hasBypass) {
                val playerCount = PlayerCache.getPlayers().size
                val maxPlayers = RediVelocity.instance.maxPlayers

                if (playerCount >= maxPlayers) {
                    deny(
                        event,
                        RediVelocity.instance.messageConfig.getString(
                            Route.fromString("kick-network-full")
                        )
                    )

                    return
                }
            }
        }

        if (RediVelocity.instance.maintenance) {
            val bypassPermission = RediVelocity.instance.config.getString(
                Route.fromString(
                    "login-configuration.maintenance.bypass-permission"
                )
            )

            if (!player.hasPermission(bypassPermission)) {
                deny(
                    event,
                    RediVelocity.instance.messageConfig.getString(
                        Route.fromString("kick-maintenance-mode")
                    )
                )

                return
            }
        }

        if (
            RediVelocity.instance.config.getBoolean(
                Route.fromString("playerversion-check.enabled")
            )
        ) {
            val allowedVersions =
                RediVelocity.instance.config.getIntList(
                    Route.fromString(
                        "playerversion-check.allowed-versions"
                    )
                )

            val bypassPermission =
                RediVelocity.instance.config.getString(
                    Route.fromString(
                        "playerversion-check.bypass-permission"
                    )
                )

            if (
                player.protocolVersion.protocol !in allowedVersions &&
                !player.hasPermission(bypassPermission)
            ) {
                deny(
                    event,
                    RediVelocity.instance.messageConfig.getString(
                        Route.fromString("playerversion_unsupported")
                    )
                )

                return
            }
        }
    }

    private fun deny(
        event: LoginEvent,
        message: String
    ) {
        event.result = ResultedEvent.ComponentResult.denied(
            MiniMessage.miniMessage().deserialize(message)
        )
    }
}