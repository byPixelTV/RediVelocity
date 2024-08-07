package de.bypixeltv.redivelocity.listeners

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.config.Config
import de.bypixeltv.redivelocity.managers.RedisController
import net.kyori.adventure.text.minimessage.MiniMessage

class PostLoginListener @Inject constructor(private val rediVelocity: RediVelocity, private val redisController: RedisController, private val config: Config, private val proxyId: String, private val proxy: ProxyServer) {

    private val miniMessage = MiniMessage.miniMessage()

    @Suppress("UNUSED")
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        redisController.sendJsonMessage(
            "postLogin",
            rediVelocity.getProxyId(),
            player.username,
            player.uniqueId.toString(),
            player.clientBrand.toString(),
            player.remoteAddress.toString().split(":")[0].substring(1),
            config.redisChannel
        )
        if (redisController.getHashField("rv-players-blacklist", player.uniqueId.toString()) != null) {
            redisController.setHashField("rv-players-blacklist", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
            player.disconnect(miniMessage.deserialize(config.kickMessage))
            return
        }
        val players = redisController.getHashField("rv-proxy-players", proxyId)?.toInt()
        if (players != null) {
            if (players <= 0) {
                redisController.setHashField("rv-proxy-players", proxyId, "1")
            } else {
                redisController.setHashField("rv-proxy-players", proxyId, (players + 1).toString())
            }
        } else {
            redisController.setHashField("rv-proxy-players", proxyId, "0")
        }
        val gplayers = redisController.getString("rv-global-playercount")
        if (gplayers != null) {
            if (gplayers.toInt() <= 0) {
                redisController.setString("rv-global-playercount", "1")
            } else {
                redisController.setString("rv-global-playercount", (gplayers.toInt() + 1).toString())
            }
        } else {
            redisController.setString("rv-global-playercount", "0")
        }
        redisController.setHashField("rv-players-proxy", player.uniqueId.toString(), proxyId)
        redisController.setHashField("rv-players-name", player.uniqueId.toString(), player.username)
        redisController.setHashField("rv-players-name", player.uniqueId.toString(), player.username)
        redisController.setHashField("rv-players-ip", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
    }
}