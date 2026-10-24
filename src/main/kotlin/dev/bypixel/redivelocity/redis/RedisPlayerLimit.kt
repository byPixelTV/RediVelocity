package dev.bypixel.redivelocity.redis

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.cache.PlayerSessionCache
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.UUID

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object RedisPlayerLimit {
    private val script = RedisPlayerLimit::class.java.getResource("/redis/max-players.lua")!!.readText()
    private val lock = Mutex()

    private fun bypassPermission(): String = RediVelocity.instance.config.getString(
        Route.fromString("login-configuration.max-players-bypass-permission")
    )

    suspend fun refreshBypass() {
        val permission = bypassPermission()
        for (player in RediVelocity.instance.proxy.allPlayers) {
            val session = PlayerSessionCache.get(player) ?: continue
            RedisPlayerState.update("bypass", player.uniqueId.toString(), session,
                player.hasPermission(permission).toString())
        }
    }

    suspend fun apply(limit: Int, requestId: String) = lock.withLock {
        val plugin = RediVelocity.instance
        refreshBypass()
        val enabled = plugin.config.getBoolean(Route.fromString("login-configuration.enabled")) &&
            plugin.config.getBoolean(Route.fromString("login-configuration.enforce-max-players"))
        val result = plugin.lettuceClient.withCoroutines {
            it.eval<String>(script, ScriptOutputType.VALUE, emptyArray<String>(),
                limit.toString(), plugin.maxPlayers.toString(), enabled.toString(), requestId)
        }
        plugin.maxPlayers = limit
        if (!enabled || plugin.stopping) return@withLock
        val selected = Json.decodeFromString<List<List<String>>>(checkNotNull(result))
        val permission = bypassPermission()
        val reason = MiniMessage.miniMessage().deserialize(
            plugin.messageConfig.getString(Route.fromString("kick-network-full"))
        )
        for ((uuid, session) in selected) {
            val player = plugin.proxy.getPlayer(UUID.fromString(uuid)).orElse(null) ?: continue
            PlayerSessionCache.withLock(player.uniqueId) {
                // A delayed command must never kick a replacement connection or a bypass player.
                if (player.isActive && PlayerSessionCache.get(player) == session &&
                    !player.hasPermission(permission)) {
                    player.disconnect(reason)
                }
            }
        }
    }
}
