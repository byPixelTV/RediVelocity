package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.cache.PlayerSessionCache
import dev.bypixel.redivelocity.redis.RedisPlayerState
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object DisconnectListener {

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        val uuid = player.uniqueId.toString()

        val sessionId = PlayerSessionCache.remove(player)

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val removed = PlayerSessionCache.withLock(player.uniqueId) {
                sessionId != null && RedisPlayerState.update("disconnect", uuid, sessionId)
            }

            if (removed) {
                RediVelocity.instance.lettuceClient.sendMessage(
                    JSONObject().apply {
                        put("action", "DISCONNECT")
                        put("uuid", uuid)
                        put("username", player.username)
                        put("ip", player.remoteAddress.address.hostAddress)
                        put("proxyId", RediVelocity.instance.proxyId)
                        put("sessionId", sessionId)
                        put("protocolVersion", player.protocolVersion.protocol)
                        put("clientBrand", player.clientBrand)
                        put("timestamp", System.currentTimeMillis())
                    },
                    "redivelocity:players"
                )
            }

            PlayercountUtil.setProxyPlayercount()
            PlayercountUtil.calcGlobalPlayercount()

            RediVelocity.instance.lettuceClient.sendMessage(
                JSONObject().apply {
                    put("action", "UPDATE")
                },
                "redivelocity:global-player-updates"
            )
        }
    }
}