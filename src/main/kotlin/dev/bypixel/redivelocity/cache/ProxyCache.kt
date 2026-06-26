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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object ProxyCache {
    private val proxies = mutableSetOf<String>()

    private val job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            proxies.clear()

            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hgetall("redivelocity:proxies").collect { kv ->
                    if (!proxies.contains(kv.value)) {
                        proxies.add(kv.value)
                    }
                }
            }

            delay((5 * 60 * 1000L).milliseconds) // Refresh every 5 minutes
        }
    }

    private object GlobalProxyCacheListener : RedisListener("redivelocity:proxy-events") {
        override fun onMessage(message: String) {
            val jMsg = JSONObject(message)

            if (jMsg.has("action") && jMsg.has("id")) {
                when (jMsg.getString("action")) {
                    "ADD" -> {
                        val id = jMsg.getString("id")

                        if (!proxies.contains(id)) {
                            proxies.add(id)
                        }
                    }
                    "REMOVE" -> {
                        val id = jMsg.getString("id")

                        if (proxies.contains(id)) {
                            proxies.remove(id)
                        }
                    }
                }
            }
        }
    }

    fun register() {
        GlobalProxyCacheListener
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hgetall("redivelocity:proxies").collect { kv ->
                    if (!proxies.contains(kv.value)) {
                        proxies.add(kv.value)
                    }
                }
            }
        }
        job.start()
    }

    suspend fun unregister() {
        RedisListener.unregisterListener(GlobalProxyCacheListener)
        job.cancelAndJoin()
    }

    fun getProxies(): List<String> = proxies.toList()
}