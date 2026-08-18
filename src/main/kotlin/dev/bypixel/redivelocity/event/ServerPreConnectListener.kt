package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.dejvokep.boostedyaml.route.Route
import net.kyori.adventure.text.minimessage.MiniMessage

object ServerPreConnectListener {

    @Subscribe
    fun onServerPreConnect(event: ServerPreConnectEvent) {
        val player = event.player

        if (
            RediVelocity.instance.config.getBoolean(
                Route.fromString("login-configuration.enabled")
            ) &&
            RediVelocity.instance.maintenance
        ) {
            val bypassPermission = RediVelocity.instance.config.getString(
                Route.fromString("login-configuration.maintenance.bypass-permission")
            )

            if (!player.hasPermission(bypassPermission)) {
                event.result = ServerPreConnectEvent.ServerResult.denied()

                player.disconnect(
                    MiniMessage.miniMessage().deserialize(
                        RediVelocity.instance.messageConfig.getString(
                            Route.fromString("kick-maintenance-mode")
                        )
                    )
                )
            }
        }
    }
}