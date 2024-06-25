package de.bypixeltv.redivelocity.listeners

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import de.bypixeltv.redivelocity.config.Config
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.managers.RedisController

class DisconnectListener @Inject constructor(private val rediVelocity: RediVelocity, private val redisController: RedisController, private val config: Config, private val proxyId: String) {

    @Suppress("UNUSED")
    @Subscribe
    fun onDisconnectEvent(event: DisconnectEvent) {
        val player = event.player
        redisController.sendJsonMessage(
            "disconnect",
            rediVelocity.getProxyId(),
            player.username,
            player.uniqueId.toString(),
            player.clientBrand.toString(),
            player.remoteAddress.toString().split(":")[0].substring(1),
            config.redisChannel
        )
        val players = redisController.getHashField("rv-proxy-players", proxyId)?.toInt()
        if (players != null) {
            redisController.setHashField("rv-proxy-players", proxyId, (players - 1).toString())
        } else {
            redisController.setHashField("rv-proxy-players", proxyId, "0")
        }
        val gplayers = redisController.getString("rv-global-playercount")
        if (gplayers != null) {
            redisController.setString("rv-global-playercount", (gplayers.toInt() - 1).toString())
        } else {
            redisController.setString("rv-global-playercount", "0")
        }
        redisController.deleteHashField("rv-players-proxy", player.uniqueId.toString())
        redisController.deleteHashField("rv-players-name", player.uniqueId.toString())
        redisController.setHashField("rv-players-lastseen", player.uniqueId.toString(), System.currentTimeMillis().toString())
    }

}