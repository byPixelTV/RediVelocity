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

package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.util.ProxyIdGenerator
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

object LeaderElectionListener : RedisListener("redivelocity:leader-election") {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun onMessage(message: String) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val jMsg = JSONObject(message)

            when (jMsg.getString("action")) {
                "SET" -> {
                    val reason = jMsg.optString("reason", null)

                    if (jMsg.has("votes")) {
                        RediVelocityLogger.info("This proxy has been elected as the new leader proxy with ${jMsg.getInt("votes")} votes.")
                        if (reason != null) {
                            RediVelocityLogger.info("Reason for election: $reason")
                        }
                    }
                }
                "REMOVED" -> {
                    RediVelocityLogger.info("This proxy is no longer the leader proxy.")
                }
                "VOTE_REQUEST" -> {
                    RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                        RediVelocity.instance.lettuceClient.withCoroutines { cnx ->
                            val allProxies = ProxyIdGenerator.getExistingIds().filter { it != RediVelocity.instance.proxyId }

                            if (allProxies.isEmpty()) {
                                return@withCoroutines
                            }

                            val rnd = SecureRandom()
                            val randomProxy = allProxies[rnd.nextInt(allProxies.size)]
                            val delayMs = 50L + rnd.nextInt(201) // 50..250 ms
                            delay(delayMs)

                            val votesForProxy = cnx
                                .hget("redivelocity:votes", randomProxy)
                                ?.toIntOrNull() ?: 0

                            cnx
                                .hset("redivelocity:votes", randomProxy, (votesForProxy + 1).toString())
                        }
                    }
                }
            }
        }
    }
}