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
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.heartbeat

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.SetArgs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlin.random.Random

object HeartbeatScheduler {
    private const val HEARTBEAT_INTERVAL_MS = 10_000L
    private const val CLEANUP_THRESHOLD_MS = 90_000L
    private const val CLEANUP_LOCK_KEY = "redivelocity:cleanup-lock"

    private fun jitterMs(): Long = Random.nextLong(0, 800L)

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        delay(jitterMs())

        while (isActive) {
            val proxyId = RediVelocity.instance.proxyId
            val commands = RediVelocity.instance.lettuceClient.commands

            try {
                val now = System.currentTimeMillis()

                // atomic heartbeat lease
                commands.hset("redivelocity:heartbeats", proxyId, now.toString())
                commands.pexpire("redivelocity:heartbeats", CLEANUP_THRESHOLD_MS)

            } catch (t: Throwable) {
                RediVelocityLogger.warn("Heartbeat write failed: ${t.message}")
            }

            val leaderId = try {
                commands.get("redivelocity:leader")
            } catch (_: Throwable) {
                null
            }

            if (leaderId == proxyId) {
                tryRunCleanupLocked()
            }

            delay(HEARTBEAT_INTERVAL_MS + jitterMs())
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun tryRunCleanupLocked() {
        val commands = RediVelocity.instance.lettuceClient.commands
        val leaderId = RediVelocity.instance.proxyId
        val lock = try {
            val args = SetArgs().nx().px(15_000)
            commands.set(CLEANUP_LOCK_KEY, leaderId, args)
        } catch (e: Throwable) {
            RediVelocityLogger.warn("Failed to acquire cleanup lock: ${e.message}")
            return
        }

        if (lock != "OK") return

        try {
            cleanupProxies()
        } finally {
            try {
                val value = commands.get(CLEANUP_LOCK_KEY)
                if (value == leaderId) {
                    commands.del(CLEANUP_LOCK_KEY)
                }
            } catch (e: Throwable) {
                RediVelocityLogger.warn("Cleanup: failed to release lock: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun cleanupProxies() {
        val commands = RediVelocity.instance.lettuceClient.commands
        val leader = RediVelocity.instance.proxyId

        // 1️⃣ Atomic snapshot
        val entries = try {
            commands.hgetall("redivelocity:heartbeats").toList()
        } catch (t: Throwable) {
            RediVelocityLogger.warn("Failed to read heartbeats: ${t.message}")
            return
        }

        val now = System.currentTimeMillis()

        for (entry in entries) {
            val proxyId = entry.key
            val rawHeartbeat = entry.value

            try {
                if (proxyId == leader) continue

                val lastSeen = rawHeartbeat.toLongOrNull() ?: continue

                if (now - lastSeen <= CLEANUP_THRESHOLD_MS) continue

                val latestStr = commands.hget("redivelocity:heartbeats", proxyId)
                val latest = latestStr?.toLongOrNull()

                if (latest != null && now - latest <= CLEANUP_THRESHOLD_MS) continue

                RediVelocityLogger.info("CLEANUP: Removing stale proxy: $proxyId")

                commands.hdel("redivelocity:proxies", proxyId)
                commands.hdel("redivelocity:heartbeats", proxyId)
                commands.hdel("redivelocity:votes", proxyId)
                commands.hdel("redivelocity:proxy:player-counts", proxyId)
                RediVelocity.instance.lettuceClient.deleteHashFieldByValueAsync(
                    "redivelocity:proxy:players", proxyId
                )
                commands.srem("redivelocity:existing-proxy-ids", proxyId)

            } catch (e: Throwable) {
                RediVelocityLogger.warn("Error cleaning proxy $proxyId: ${e.message}")
            }
        }
    }
}
