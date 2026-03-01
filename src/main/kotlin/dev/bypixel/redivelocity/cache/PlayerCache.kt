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

package dev.bypixel.redivelocity.cache

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object PlayerCache {
    private val players = ConcurrentHashMap<UUID, String>()

    private val job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            players.clear()

            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hgetall("redivelocity:player:names").collect { kv ->
                    players[UUID.fromString(kv.key)] = kv.value
                }
            }

            delay(5 * 60 * 1000L) // Refresh every 5 minutes
        }
    }

    private object GlobalPlayerCacheListener : RedisListener("redivelocity:players") {
        override fun onMessage(message: String) {
            val jMsg = JSONObject(message)

            if (jMsg.has("action") && jMsg.has("uuid") && jMsg.has("username")) {
                when (jMsg.getString("action")) {
                    "POST_LOGIN" -> {
                        val uuid = UUID.fromString(jMsg.getString("uuid"))
                        val username = jMsg.getString("username")

                        players[uuid] = username
                    }
                    "PRE_LOGIN" -> {
                        val uuid = UUID.fromString(jMsg.getString("uuid"))
                        val username = jMsg.getString("username")

                        players[uuid] = username
                    }
                    "DISCONNECT" -> {
                        val uuid = UUID.fromString(jMsg.getString("uuid"))

                        players.remove(uuid)
                    }
                }
            }
        }
    }

    fun register() {
        GlobalPlayerCacheListener
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hgetall("redivelocity:player:names").collect { kv ->
                    players[UUID.fromString(kv.key)] = kv.value
                }
            }
        }
        job.start()
    }

    suspend fun unregister() {
        RedisListener.unregisterListener(GlobalPlayerCacheListener)
        job.cancelAndJoin()
    }

    fun getPlayers(): ConcurrentHashMap<UUID, String> = players
}