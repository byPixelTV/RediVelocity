package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.network.HandshakeIntent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.*

object PreLoginListener {

    private const val HEARTBEAT_TIMEOUT_MS = 90_000L

    @Subscribe
    fun onPreLogin(event: PreLoginEvent): EventTask {
        return EventTask.async {
            runBlocking {
                handlePreLogin(event)
            }
        }
    }

    private suspend fun handlePreLogin(
        event: PreLoginEvent
    ) {
        val uuid = event.uniqueId ?: return
        val username = event.username
        val version = event.connection.protocolVersion

        val ignoreSamePlayerOnTransfer =
            RediVelocity.instance.config.getBoolean(
                Route.fromString(
                    "ignore-same-players-when-transfer"
                )
            )

        val isLegitTransfer =
            event.connection.handshakeIntent == HandshakeIntent.TRANSFER &&
                    ignoreSamePlayerOnTransfer

        val remoteAddress =
            event.connection.remoteAddress

        val ip =
            if (remoteAddress is InetSocketAddress) {
                remoteAddress.address.hostAddress
            } else {
                "Unknown"
            }

        val allowSamePlayer =
            RediVelocity.instance.config.getBoolean(
                Route.fromString(
                    "allow-same-player-on-multiple-proxies"
                )
            )

        if (!allowSamePlayer && !isLegitTransfer) {
            val cachedProxy =
                PlayerCache.getPlayerProxies()[uuid]

            val cachedOnline =
                PlayerCache.getPlayers().containsKey(uuid) &&
                        !cachedProxy.isNullOrBlank()

            if (cachedOnline) {
                val onlineState = try {
                    checkOnlineState(uuid)
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "Failed to verify online state for " +
                                "$username ($uuid): ${t.message}"
                    )

                    OnlineState(
                        online = true,
                        proxyId = cachedProxy
                    )
                }

                if (onlineState.online) {
                    denyAlreadyConnected(
                        event,
                        onlineState.proxyId
                            ?: cachedProxy
                            ?: "Unknown"
                    )

                    return
                }

                PlayerCache.remove(uuid)
            }
        }

        RediVelocity.instance.lettuceClient.sendMessage(
            JSONObject().apply {
                put("action", "PRE_LOGIN")
                put("uuid", uuid.toString())
                put("username", username)
                put("ip", ip)
                put(
                    "proxyId",
                    RediVelocity.instance.proxyId
                )
                put(
                    "protocolVersion",
                    version.protocol
                )
                put(
                    "timestamp",
                    System.currentTimeMillis()
                )
            },
            "redivelocity:players"
        )
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun checkOnlineState(
        uuid: UUID
    ): OnlineState {
        return RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val uuidString = uuid.toString()
            val proxyId = redis.hget(
                "redivelocity:player:proxies",
                uuidString
            )

            if (proxyId.isNullOrBlank()) {
                return@withCoroutines OnlineState(
                    online = false,
                    proxyId = null
                )
            }

            val registeredProxy = redis.hget(
                "redivelocity:proxies",
                proxyId
            )

            if (registeredProxy == null) {
                return@withCoroutines OnlineState(
                    online = false,
                    proxyId = proxyId
                )
            }

            val sessionId = redis.hget(
                "redivelocity:player:sessions",
                uuidString
            )

            if (sessionId.isNullOrBlank()) {
                return@withCoroutines OnlineState(
                    online = false,
                    proxyId = proxyId
                )
            }

            val heartbeat = redis.hget(
                "redivelocity:heartbeats",
                proxyId
            )

            val lastSeen =
                heartbeat?.toLongOrNull() ?: return@withCoroutines OnlineState(
                    online = false,
                    proxyId = proxyId
                )

            val age =
                System.currentTimeMillis() - lastSeen

            if (age > HEARTBEAT_TIMEOUT_MS) {
                return@withCoroutines OnlineState(
                    online = false,
                    proxyId = proxyId
                )
            }

            val latestProxy = redis.hget(
                "redivelocity:player:proxies",
                uuidString
            )

            if (latestProxy != proxyId) {
                return@withCoroutines OnlineState(
                    online = true,
                    proxyId = latestProxy ?: proxyId
                )
            }

            OnlineState(
                online = true,
                proxyId = proxyId
            )
        }
    }

    private fun denyAlreadyConnected(
        event: PreLoginEvent,
        proxyId: String
    ) {
        val kickMessage =
            RediVelocity.instance.messageConfig.getString(
                Route.fromString(
                    "player_already_connected_to_network"
                )
            ) ?: "<red>You are already connected to the network!"

        event.result =
            PreLoginEvent.PreLoginComponentResult.denied(
                MiniMessage.miniMessage().deserialize(
                    kickMessage,
                    Placeholder.unparsed(
                        "proxy",
                        proxyId
                    )
                )
            )
    }

    private data class OnlineState(
        val online: Boolean,
        val proxyId: String?
    )
}