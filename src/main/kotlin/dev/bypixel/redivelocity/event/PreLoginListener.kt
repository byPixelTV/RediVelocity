package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.json.JSONObject
import java.net.InetSocketAddress

object PreLoginListener {

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onPreLogin(event: PreLoginEvent) {
        val uuid = event.uniqueId ?: return
        val username = event.username
        val version = event.connection.protocolVersion

        val remoteAddress = event.connection.remoteAddress
        val ip = if (remoteAddress is InetSocketAddress) remoteAddress.address.hostAddress else remoteAddress.toString()

        val allowSamePlayer = RediVelocity.instance.config.getBoolean(
            Route.fromString("allow-same-player-on-multiple-proxies")
        )

        val isAlreadyOnline = PlayerCache.getPlayers().containsKey(uuid)
        val playerProxy = PlayerCache.getPlayerProxies()[uuid]

        if (isAlreadyOnline && !allowSamePlayer) {
            val kickMessage = RediVelocity.instance.messageConfig.getString(
                Route.fromString("player_already_connected_to_network")
            ) ?: "<red>You are already connected to the network!"

            event.result = PreLoginEvent.PreLoginComponentResult.denied(
                MiniMessage.miniMessage().deserialize(kickMessage, Placeholder.unparsed("proxy", playerProxy ?: "Unknown"))
            )
            return
        }

        RediVelocity.instance.lettuceClient.sendMessage(JSONObject().apply {
            put("action", "PRE_LOGIN")
            put("uuid", uuid.toString())
            put("username", username)
            put("ip", ip)
            put("proxyId", RediVelocity.instance.proxyId)
            put("protocolVersion", version.protocol)
            put("timestamp", System.currentTimeMillis())
        }, "redivelocity:players")
    }
}