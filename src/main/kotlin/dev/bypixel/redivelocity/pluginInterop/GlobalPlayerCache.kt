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

package dev.bypixel.redivelocity.pluginInterop

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object GlobalPlayerCache {
    private val players = ConcurrentHashMap<UUID, String>()

    private object GlobalPlayerCacheListener : RedisListener("redivelocity:players") {
        override fun onMessage(message: String) {
            val jMsg = JSONObject(message)

            if (jMsg.has("action") && jMsg.has("uuid") && jMsg.has("username")) {
                when (jMsg.getString("action")) {
                    "POST_LOGIN" -> {
                        val uuid = UUID.fromString(jMsg.getString("uuid"))
                        val username = jMsg.getString("username")

                        GlobalPlayerCache.players[uuid] = username
                    }
                    "DISCONNECT" -> {
                        val uuid = UUID.fromString(jMsg.getString("uuid"))

                        GlobalPlayerCache.players.remove(uuid)
                    }
                }
            }
        }
    }

    init {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:player:names").collect { kv ->
                players[UUID.fromString(kv.key)] = kv.value
            }
        }
    }

    fun register() {
        GlobalPlayerCacheListener
    }

    fun unregister() {
        RedisListener.unregisterListener(GlobalPlayerCacheListener)
    }

    fun getPlayers(): ConcurrentHashMap<UUID, String> = players
}