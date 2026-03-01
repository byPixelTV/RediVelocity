package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import net.kyori.adventure.text.minimessage.MiniMessage
import org.json.JSONObject

object PreLoginListener {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onPreLogin(event: PreLoginEvent) {
        val uuid = event.uniqueId
        val username = event.username
        val version = event.connection.protocolVersion
        val ip = event.connection.remoteAddress.toString().split(":")[0].substring(1)

        if (uuid != null) {
            val onlineUuids = PlayerCache.getPlayers()

            if (onlineUuids.contains(uuid.toString())) {
                if (RediVelocity.instance.config.getBoolean(
                        Route.fromString("allow-same-player-on-multiple-proxies")
                )) {
                    // cancel the event to prevent the player from conncting to the server and then remove the player from the cache so they can connect again
                    event.result = PreLoginEvent.PreLoginComponentResult.denied(MiniMessage.miniMessage().deserialize(
                        RediVelocity.instance.messageConfig.getString(Route.fromString("player_already_connected_to_network"))))
                }
            } else {
                RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
                    put("action", "PRE_LOGIN")
                    put("uuid", uuid.toString())
                    put("username", username)
                    put("ip", ip)
                    put("proxyId", RediVelocity.instance.proxyId)
                    put("protocolVersion",version)
                    put("timestamp", System.currentTimeMillis())
                }, "redivelocity:players")
            }
        }
    }
}