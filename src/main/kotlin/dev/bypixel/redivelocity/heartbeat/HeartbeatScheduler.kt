package dev.bypixel.redivelocity.heartbeat

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.SetArgs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

object HeartbeatScheduler {

    private const val HEARTBEAT_INTERVAL_MS = 10_000L
    private const val CLEANUP_THRESHOLD_SECONDS = 90L
    private const val CLEANUP_THRESHOLD_MS = CLEANUP_THRESHOLD_SECONDS * 1000L

    private const val CLEANUP_LOCK_KEY = "redivelocity:cleanup-lock"

    private fun jitterMs(): Long = Random.nextLong(0, 800L)

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        delay(jitterMs().milliseconds)

        while (isActive) {
            val proxyId = RediVelocity.instance.proxyId

            try {
                val now = System.currentTimeMillis()

                RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                    redis.hset(
                        "redivelocity:heartbeats",
                        proxyId,
                        now.toString()
                    )

                    redis.hexpire(
                        "redivelocity:heartbeats",
                        CLEANUP_THRESHOLD_SECONDS,
                        proxyId
                    )
                }
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Heartbeat write failed: ${t.message}"
                )
            }

            val leaderId = try {
                RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                    redis.hget(
                        "redivelocity:leader",
                        "leader-id"
                    )
                }
            } catch (_: Throwable) {
                null
            }

            if (leaderId == proxyId) {
                tryRunCleanupLocked()
            }

            delay(
                (HEARTBEAT_INTERVAL_MS + jitterMs()).milliseconds
            )
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun tryRunCleanupLocked() {
        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val leaderId = RediVelocity.instance.proxyId
            val lock = try {
                val args = SetArgs()
                    .nx()
                    .px(15_000)

                redis.set(
                    CLEANUP_LOCK_KEY,
                    leaderId,
                    args
                )
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Failed to acquire cleanup lock: ${t.message}"
                )

                return@withCoroutines
            }

            if (lock != "OK") {
                return@withCoroutines
            }

            try {
                cleanupProxies()
            } finally {
                try {
                    val value = redis.get(
                        CLEANUP_LOCK_KEY
                    )

                    if (value == leaderId) {
                        redis.del(
                            CLEANUP_LOCK_KEY
                        )
                    }
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "Cleanup: failed to release lock: ${t.message}"
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun cleanupProxies() {
        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val selfId = RediVelocity.instance.proxyId
            val now = System.currentTimeMillis()

            val proxyIds = try {
                redis.hkeys(
                    "redivelocity:proxies"
                )
                    .toList()
                    .toSet()
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Failed to read proxies: ${t.message}"
                )

                return@withCoroutines
            }

            val heartbeats = try {
                redis.hgetall(
                    "redivelocity:heartbeats"
                )
                    .toList()
                    .associate { it.key to it.value }
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Failed to read heartbeats: ${t.message}"
                )

                return@withCoroutines
            }

            suspend fun removeProxy(
                proxyId: String,
                reason: String
            ) {
                RediVelocityLogger.info(
                    "CLEANUP: Removing proxy $proxyId ($reason)"
                )

                val affectedPlayers = try {
                    redis.hgetall(
                        "redivelocity:player:proxies"
                    )
                        .toList()
                        .filter { it.value == proxyId }
                        .map { it.key }
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "CLEANUP: Failed to read players for proxy $proxyId: ${t.message}"
                    )

                    emptyList()
                }

                for (uuid in affectedPlayers) {
                    try {
                        val currentProxy = redis.hget(
                            "redivelocity:player:proxies",
                            uuid
                        )

                        if (currentProxy != proxyId) {
                            continue
                        }

                        val username = redis.hget(
                            "redivelocity:player:names",
                            uuid
                        )

                        val sessionId = redis.hget(
                            "redivelocity:player:sessions",
                            uuid
                        )

                        val ip = redis.hget(
                            "redivelocity:player:ips",
                            uuid
                        )

                        redis.hdel(
                            "redivelocity:player:servers",
                            uuid
                        )

                        redis.hdel(
                            "redivelocity:player:names",
                            uuid
                        )

                        redis.hdel(
                            "redivelocity:player:proxies",
                            uuid
                        )

                        redis.hdel(
                            "redivelocity:player:sessions",
                            uuid
                        )

                        redis.hdel(
                            "redivelocity:player:ips",
                            uuid
                        )

                        if (username != null) {
                            RediVelocity.instance.lettuceClient.sendMessage(
                                JSONObject().apply {
                                    put(
                                        "action",
                                        "DISCONNECT"
                                    )

                                    put(
                                        "uuid",
                                        uuid
                                    )

                                    put(
                                        "username",
                                        username
                                    )

                                    put(
                                        "proxyId",
                                        proxyId
                                    )

                                    put(
                                        "timestamp",
                                        System.currentTimeMillis()
                                    )

                                    if (sessionId != null) {
                                        put(
                                            "sessionId",
                                            sessionId
                                        )
                                    }

                                    if (ip != null) {
                                        put(
                                            "ip",
                                            ip
                                        )
                                    }
                                },
                                "redivelocity:players"
                            )
                        }

                        RediVelocityLogger.info(
                            "CLEANUP: Removed stale player $uuid from dead proxy $proxyId"
                        )
                    } catch (t: Throwable) {
                        RediVelocityLogger.warn(
                            "CLEANUP: Failed to remove stale player $uuid from proxy $proxyId: ${t.message}"
                        )
                    }
                }

                redis.hdel(
                    "redivelocity:proxies",
                    proxyId
                )

                redis.hdel(
                    "redivelocity:heartbeats",
                    proxyId
                )

                redis.hdel(
                    "redivelocity:votes",
                    proxyId
                )

                redis.hdel(
                    "redivelocity:proxy:player-counts",
                    proxyId
                )

                redis.del(
                    "redivelocity:registered-servers:$proxyId"
                )

                redis.srem(
                    "redivelocity:existing-proxy-ids",
                    proxyId
                )

                val currentLeader = redis.hget(
                    "redivelocity:leader",
                    "leader-id"
                )

                if (currentLeader == proxyId) {
                    redis.hdel(
                        "redivelocity:leader",
                        "leader-id"
                    )
                }

                RediVelocityLogger.info(
                    "CLEANUP: Proxy $proxyId removed successfully"
                )
            }

            for (proxyId in proxyIds) {
                if (proxyId == selfId) {
                    continue
                }

                try {
                    val rawHeartbeat = heartbeats[
                        proxyId
                    ]

                    if (rawHeartbeat == null) {
                        removeProxy(
                            proxyId,
                            "orphan: no heartbeat entry"
                        )

                        continue
                    }

                    val lastSeen = rawHeartbeat.toLongOrNull()

                    if (lastSeen == null) {
                        removeProxy(
                            proxyId,
                            "invalid heartbeat value"
                        )

                        continue
                    }

                    if (
                        now - lastSeen <=
                        CLEANUP_THRESHOLD_MS
                    ) {
                        continue
                    }

                    val latest = redis.hget(
                        "redivelocity:heartbeats",
                        proxyId
                    )?.toLongOrNull()

                    if (
                        latest != null &&
                        now - latest <= CLEANUP_THRESHOLD_MS
                    ) {
                        continue
                    }

                    removeProxy(
                        proxyId,
                        "stale heartbeat"
                    )
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "Error cleaning proxy $proxyId: ${t.message}"
                    )
                }
            }

            for (heartbeatProxyId in heartbeats.keys) {
                if (heartbeatProxyId == selfId) {
                    continue
                }

                if (heartbeatProxyId in proxyIds) {
                    continue
                }

                try {
                    removeProxy(
                        heartbeatProxyId,
                        "orphan: heartbeat without proxy entry"
                    )
                } catch (t: Throwable) {
                    RediVelocityLogger.warn(
                        "Error cleaning dangling heartbeat $heartbeatProxyId: ${t.message}"
                    )
                }
            }
        }
    }
}