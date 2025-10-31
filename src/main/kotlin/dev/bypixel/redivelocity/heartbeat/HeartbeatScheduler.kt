/*
 * Copyright (c) 2025.
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object HeartbeatScheduler {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {

        delay(10000L)

        while (isActive) {
            RediVelocity.instance.lettuceClient.commands.hset("redivelocity:heartbeats", RediVelocity.instance.proxyId, System.currentTimeMillis().toString())

            val leaderProxy = RediVelocity.instance.lettuceClient.commands.get("redivelocity:leader")

            if (leaderProxy == null) {
                delay(10000L)
                continue
            }

            if (RediVelocity.instance.proxyId == leaderProxy) {
                cleanupProxies()
            }
            delay(10000L)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun cleanupProxies() {
        val heartbeats = mutableMapOf<String, Long?>()

        RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:heartbeats").collect {
            heartbeats[it.key] = it.value.toLongOrNull()
        }

        val currentTime = System.currentTimeMillis()

        for (proxyId in heartbeats.keys) {
            val heartbeatStr = heartbeats[proxyId]

            if (heartbeatStr == null || currentTime - heartbeatStr > 30000) {
                RediVelocity.instance.lettuceClient.commands.hdel("redivelocity:proxies", proxyId)
                RediVelocity.instance.lettuceClient.commands.hdel("redivelocity:heartbeats", proxyId)
                RediVelocity.instance.lettuceClient.commands.hdel("redivelocity:votes", proxyId)
                RediVelocity.instance.lettuceClient.commands.hdel("redivelocity:proxy:player-counts", proxyId)
                RediVelocity.instance.lettuceClient.deleteHashFieldByValueAsync("redivelocity:proxy:players", proxyId)
                RediVelocity.instance.lettuceClient.commands.srem("redivelocity:existing-proxy-ids", proxyId)
            }
        }
    }
}