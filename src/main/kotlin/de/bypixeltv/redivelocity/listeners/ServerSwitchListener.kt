package de.bypixeltv.redivelocity.listeners

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent
import de.bypixeltv.redivelocity.config.Config
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.managers.RedisController

class ServerSwitchListener @Inject constructor(private val rediVelocity: RediVelocity, private val redisController: RedisController, private val config: Config) {

    @Suppress("UNUSED")
    @Subscribe
    fun onServerSwitch(event: ServerConnectedEvent) {
        val player = event.player
        redisController.sendJsonMessage(
            "serverSwitch",
            rediVelocity.getProxyId(),
            player.username,
            player.uniqueId.toString(),
            player.clientBrand.toString(),
            player.remoteAddress.toString().split(":")[0].substring(1),
            config.redisChannel
        )
    }

}