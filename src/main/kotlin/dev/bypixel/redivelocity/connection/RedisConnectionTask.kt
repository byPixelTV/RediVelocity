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

package dev.bypixel.redivelocity.connection

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.cache.PlayerSessionCache
import dev.bypixel.redivelocity.redis.RedisPlayerState
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.toList
import java.util.UUID

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object RedisConnectionTask {
    val job = RediVelocityCoroutineScope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
        while (isActive) {
            try {
                if (!RediVelocity.instance.lettuceClient.connection.isOpen || RediVelocity.instance.lettuceClient.checkConnectionErrors().isNotEmpty()) {
                    RediVelocityLogger.warn("Redis connection was lost. Attempting to reconnect...")
                    RediVelocity.instance.lettuceClient.reconnectAll()
                }
                reconcilePlayers()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                RediVelocityLogger.warn("Failed to reconcile Redis player sessions: ${e.message}")
            }
            delay(5000L)
        }
    }

    private suspend fun reconcilePlayers() {
        val plugin = RediVelocity.instance
        val candidates = plugin.lettuceClient.withCoroutines { redis ->
            redis.hgetall("redivelocity:player:proxies").toList()
                .filter { it.value == plugin.proxyId }
                .mapNotNull { entry ->
                    val session = redis.hget("redivelocity:player:sessions", entry.key)
                    if (session.isNullOrBlank()) null else entry.key to session
                }
        }
        for ((id, session) in candidates) {
            val uuid = try { UUID.fromString(id) } catch (_: IllegalArgumentException) { continue }
            PlayerSessionCache.withLock(uuid) {
                if (!plugin.stopping && !RediVelocity.server.getPlayer(uuid).map { it.isActive }.orElse(false)) {
                    RedisPlayerState.update("reconcile", id, session)
                }
            }
        }
    }
}
