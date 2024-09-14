package de.bypixeltv.redivelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.ServerConnectedEvent
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.config.Config
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class ServerSwitchListener @Inject constructor(
    private val rediVelocity: RediVelocity,
    private val config: Config
) {

    private val redisController = rediVelocity.getRedisController()

    @Suppress("UNUSED")
    @Subscribe
    fun onServerSwitch(event: ServerConnectedEvent) {
        val player = event.player
        val previousServerName = event.previousServer.map { it.serverInfo.name }.orElse("null")
        config.redis.let {
            redisController.sendServerSwitchMessage(
                "serverSwitch",
                rediVelocity.getProxyId(),
                player.username,
                player.uniqueId.toString(),
                player.clientBrand.toString(),
                player.remoteAddress.toString().split(":")[0].substring(1),
                event.server.serverInfo.name ?: "null",
                previousServerName,
                it.channel
            )
        }
        redisController.setHashField("rv-players-server", player.uniqueId.toString(), event.server.serverInfo.name)
    }

}