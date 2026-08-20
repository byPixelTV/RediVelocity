/*
 * Copyright (c) 2024-present byPixelTV & contributors.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.cache

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object PlayerCache {
    private const val HEARTBEAT_TIMEOUT_MS = 90_000L

    private val players = ConcurrentHashMap<UUID, String>()
    private val playerProxies = ConcurrentHashMap<UUID, String>()
    private val playerSessions = ConcurrentHashMap<UUID, String>()

    private var refreshJob: Job? = null

    private object GlobalPlayerCacheListener :
        RedisListener("redivelocity:players") {

        override fun onMessage(message: String) {
            try {
                val json = JSONObject(message)

                if (!json.has("action") || !json.has("uuid")) {
                    return
                }

                val uuid = try {
                    UUID.fromString(json.getString("uuid"))
                } catch (_: IllegalArgumentException) {
                    return
                }

                when (json.getString("action")) {
                    "POST_LOGIN" -> {
                        if (
                            !json.has("username") ||
                            !json.has("proxyId")
                        ) {
                            return
                        }

                        val username = json.getString("username")
                        val proxyId = json.getString("proxyId")

                        playerProxies[uuid] = proxyId

                        if (
                            json.has("sessionId") &&
                            !json.isNull("sessionId")
                        ) {
                            playerSessions[uuid] =
                                json.getString("sessionId")
                        } else {
                            playerSessions.remove(uuid)
                        }

                        players[uuid] = username
                    }

                    "DISCONNECT" -> {
                        val incomingSession =
                            if (
                                json.has("sessionId") &&
                                !json.isNull("sessionId")
                            ) {
                                json.getString("sessionId")
                            } else {
                                null
                            }

                        val cachedSession =
                            playerSessions[uuid]

                        if (
                            incomingSession != null &&
                            cachedSession != null &&
                            incomingSession != cachedSession
                        ) {
                            return
                        }

                        remove(uuid)
                    }
                }
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Failed to process player cache message: ${t.message}"
                )
            }
        }
    }

    fun register() {
        GlobalPlayerCacheListener

        refreshJob = RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            refresh()

            while (isActive) {
                delay(5.minutes)

                try {
                    refresh()
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "Failed to refresh PlayerCache: ${t.message}"
                    )
                }
            }
        }
    }

    private suspend fun refresh() {
        val now = System.currentTimeMillis()

        val snapshot = RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val registeredProxies = redis
                .hkeys("redivelocity:proxies")
                .toList()
                .toSet()

            val heartbeats = redis
                .hgetall("redivelocity:heartbeats")
                .toList()
                .associate { it.key to it.value }

            val aliveProxies = registeredProxies.filterTo(HashSet()) { proxyId ->
                val lastSeen = heartbeats[proxyId]
                    ?.toLongOrNull()
                    ?: return@filterTo false

                now - lastSeen <= HEARTBEAT_TIMEOUT_MS
            }

            val redisPlayerProxies = redis
                .hgetall("redivelocity:player:proxies")
                .toList()

            val validPlayerProxies =
                HashMap<UUID, String>()

            for (entry in redisPlayerProxies) {
                val uuid = try {
                    UUID.fromString(entry.key)
                } catch (_: IllegalArgumentException) {
                    continue
                }

                val proxyId = entry.value

                if (proxyId !in aliveProxies) {
                    continue
                }

                validPlayerProxies[uuid] = proxyId
            }

            val validUuids =
                validPlayerProxies.keys

            val redisNames = redis
                .hgetall("redivelocity:player:names")
                .toList()

            val validPlayers =
                HashMap<UUID, String>()

            for (entry in redisNames) {
                val uuid = try {
                    UUID.fromString(entry.key)
                } catch (_: IllegalArgumentException) {
                    continue
                }

                if (uuid !in validUuids) {
                    continue
                }

                validPlayers[uuid] = entry.value
            }

            val redisSessions = redis
                .hgetall("redivelocity:player:sessions")
                .toList()

            val validSessions =
                HashMap<UUID, String>()

            for (entry in redisSessions) {
                val uuid = try {
                    UUID.fromString(entry.key)
                } catch (_: IllegalArgumentException) {
                    continue
                }

                if (uuid !in validUuids) {
                    continue
                }

                validSessions[uuid] = entry.value
            }

            PlayerCacheSnapshot(
                players = validPlayers,
                proxies = validPlayerProxies,
                sessions = validSessions
            )
        }

        playerProxies.keys.removeIf {
            it !in snapshot.proxies
        }

        playerProxies.putAll(
            snapshot.proxies
        )

        playerSessions.keys.removeIf {
            it !in snapshot.sessions
        }

        playerSessions.putAll(
            snapshot.sessions
        )

        players.keys.removeIf {
            it !in snapshot.players
        }

        players.putAll(
            snapshot.players
        )
    }

    suspend fun unregister() {
        RedisListener.unregisterListener(
            GlobalPlayerCacheListener
        )

        refreshJob?.cancel()
        refreshJob?.join()
        refreshJob = null

        players.clear()
        playerProxies.clear()
        playerSessions.clear()
    }

    fun remove(uuid: UUID) {
        players.remove(uuid)
        playerProxies.remove(uuid)
        playerSessions.remove(uuid)
    }

    fun getPlayers(): ConcurrentHashMap<UUID, String> =
        players

    fun getPlayerProxies(): ConcurrentHashMap<UUID, String> =
        playerProxies

    fun getPlayerSessions(): ConcurrentHashMap<UUID, String> =
        playerSessions

    private data class PlayerCacheSnapshot(
        val players: Map<UUID, String>,
        val proxies: Map<UUID, String>,
        val sessions: Map<UUID, String>
    )
}