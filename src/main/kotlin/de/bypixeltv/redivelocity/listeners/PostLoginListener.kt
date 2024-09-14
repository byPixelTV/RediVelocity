package de.bypixeltv.redivelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PostLoginEvent
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.config.Config
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.kyori.adventure.text.minimessage.MiniMessage

@Singleton
class PostLoginListener @Inject constructor(
    private val rediVelocity: RediVelocity,
    private val config: Config
) {

    private val miniMessage = MiniMessage.miniMessage()

    private val redisController = rediVelocity.getRedisController()
    private val proxyId = rediVelocity.getProxyId()

    @Suppress("UNUSED")
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player

        if (config.versionControl.enabled) {
            val playerProtocolVersion = player.protocolVersion.protocol
            val requiredProtocolVersion = config.versionControl.protocolVersion
            if (playerProtocolVersion < requiredProtocolVersion) {
                if (!player.hasPermission("redivelocity.admin.version.bypass")) {
                    player.disconnect(miniMessage.deserialize(config.versionControl.kickMessage))
                }
            }
        }

        config.redis.let {
            redisController.sendPostLoginMessage(
                "postLogin",
                rediVelocity.getProxyId(),
                player.username,
                player.uniqueId.toString(),
                player.currentServer.get().serverInfo.name,
                player.remoteAddress.toString().split(":")[0].substring(1),
                it.channel
            )
        }
        if (redisController.getHashField("rv-players-blacklist", player.uniqueId.toString()) != null) {
            redisController.setHashField("rv-players-blacklist", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
            player.disconnect(config.messages.let { miniMessage.deserialize(it.kickMessage) })
            return
        }
        redisController.setHashField("rv-players-proxy", player.uniqueId.toString(), proxyId)
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
        redisController.setHashField("rv-players-name", player.uniqueId.toString(), player.username)
        redisController.setHashField("rv-players-ip", player.uniqueId.toString(), player.remoteAddress.toString().split(":")[0].substring(1))
    }
}