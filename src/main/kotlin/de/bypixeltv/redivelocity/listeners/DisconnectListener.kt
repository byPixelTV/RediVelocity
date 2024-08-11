package de.bypixeltv.redivelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.config.Config
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class DisconnectListener @Inject constructor(
    private val rediVelocity: RediVelocity,
    private val config: Config
) {

    private val redisController = rediVelocity.getRedisController()
    private val proxyId = rediVelocity.getProxyId()

    @Suppress("UNUSED")
    @Subscribe
    fun onDisconnectEvent(event: DisconnectEvent) {
        val player = event.player
        config.redis.channel.let {
            redisController.sendJsonMessage(
                "disconnect",
                rediVelocity.getProxyId(),
                player.username,
                player.uniqueId.toString(),
                player.clientBrand.toString(),
                player.remoteAddress.toString().split(":")[0].substring(1),
                it
            )
        }
        val players = redisController.getHashField("rv-proxy-players", proxyId)?.toInt()
        if (players != null) {
            if (players <= 0) {
                redisController.setHashField("rv-proxy-players", proxyId, "0")
            } else {
                redisController.setHashField("rv-proxy-players", proxyId, (players - 1).toString())
            }
        } else {
            redisController.setHashField("rv-proxy-players", proxyId, "0")
        }
        val gplayers = redisController.getString("rv-global-playercount")
        if (gplayers != null) {
            if (gplayers.toInt() <= 0) {
                redisController.setString("rv-global-playercount", "0")
            } else {
                redisController.setString("rv-global-playercount", (gplayers.toInt() - 1).toString())
            }
        } else {
            redisController.setString("rv-global-playercount", "0")
        }
        redisController.deleteHashField("rv-players-proxy", player.uniqueId.toString())
        redisController.deleteHashField("rv-players-name", player.uniqueId.toString())
        redisController.setHashField("rv-players-lastseen", player.uniqueId.toString(), System.currentTimeMillis().toString())
        redisController.deleteHashField("rv-players-server", player.uniqueId.toString())
    }

}