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

package dev.bypixel.redivelocity.election

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.model.LeaderRemovedMsg
import dev.bypixel.redivelocity.model.LeaderSetMsg
import dev.bypixel.redivelocity.model.LeaderVoteMsg
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.security.SecureRandom

object ElectionScheduler {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        val electionCounter = intArrayOf(0)

        delay(15000)

        while (isActive) {
            val activeProxies = RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxies").toList()

            if (activeProxies.isEmpty()) {
                // No active proxies, skip election and wait for the next cycle
                delay(15000)
                continue
            }

            val currentLeader = RediVelocity.instance.lettuceClient.commands.get("redivelocity:leader")

            if (currentLeader == null) {
                RediVelocity.instance.lettuceClient.commands.set("redivelocity:leader", RediVelocity.instance.proxyId)
            } else {
                val leaderHeartbeat = RediVelocity.instance.lettuceClient.commands.hget("redivelocity:heartbeats", currentLeader)?.toLongOrNull()

                if (leaderHeartbeat == null || System.currentTimeMillis() - leaderHeartbeat > 30000) {
                    RediVelocity.instance.lettuceClient.commands.del("redivelocity:leader")
                }
            }

            if (currentLeader != RediVelocity.instance.proxyId) {
                delay(15000)
                continue
            }

            val forceNewElection = electionCounter[0]++ % 20 == 0
            val needNewLeader = !activeProxies.contains(currentLeader) || forceNewElection

            if (needNewLeader) {
                val reason = if (!activeProxies.contains(currentLeader)) "Leader $currentLeader is not active" else "Scheduled forced election"

                if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                    RediVelocityLogger.info("Starting leader election. Reason: $reason")
                }

                RediVelocity.instance.lettuceClient.sendMessage(
                    JSONObject(
                        Json.encodeToString(
                            LeaderVoteMsg(action = "VOTE_REQUEST")
                        )
                    ), "redivelocity:leader-election"
                )

                delay(2500)

                RediVelocity.instance.lettuceClient.commands.del("redivelocity:leader")

                val allVotes = RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:votes").toList().mapNotNull { kv -> kv.value?.let { value -> kv.key to value } }
                    .toMap()

                val voteCount = allVotes.map { it.key to it.value.toInt() }.toMap()

                if (voteCount.isEmpty()) {
                    val newLeader = activeProxies[SecureRandom().nextInt(activeProxies.size)]

                    RediVelocity.instance.lettuceClient.commands.set("redivelocity:leader", newLeader)

                    if (newLeader != currentLeader) {
                        RediVelocity.instance.lettuceClient.sendMessage(JSONObject(
                            Json.encodeToString(LeaderRemovedMsg(action = "REMOVED", recipient = currentLeader))
                        ), "redivelocity:leader-election")
                    }

                    if (newLeader == RediVelocity.instance.proxyId) {
                        if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                            RediVelocityLogger.info("This proxy (${RediVelocity.instance.proxyId}) has been elected as leader by random selection (random election).")
                        }
                    } else {
                        RediVelocity.instance.lettuceClient.sendMessage(JSONObject(
                            Json.encodeToString(
                                LeaderSetMsg(action = "SET", recipient = newLeader, reason = "Random election due to no votes", votes = 0)
                            )
                        ), "redivelocity:leader-election")
                    }

                    delay(15000)
                    continue
                }

                val groupedByVotes: MutableMap<Long, MutableList<String>> = HashMap()

                voteCount.forEach { (proxy, count) ->
                    groupedByVotes.computeIfAbsent(count.toLong()) { ArrayList() }.add(proxy)
                }

                val maxVotes: Long = groupedByVotes.keys.maxOrNull() ?: 0L

                val topCandidates = groupedByVotes[maxVotes] ?: emptyList()

                val newLeader = topCandidates[SecureRandom().nextInt(topCandidates.size)]
                RediVelocity.instance.lettuceClient.commands.set("redivelocity:leader", newLeader)

                if (newLeader != currentLeader) {
                    RediVelocity.instance.lettuceClient.sendMessage(JSONObject(
                        Json.encodeToString(LeaderRemovedMsg(action = "REMOVED", recipient = currentLeader))
                    ), "redivelocity:leader-election")
                }

                if (newLeader == RediVelocity.instance.proxyId) {
                    if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                        RediVelocityLogger.info("This proxy (${RediVelocity.instance.proxyId}) has been elected as leader with $maxVotes votes.")
                    }
                } else {
                    RediVelocity.instance.lettuceClient.sendMessage(JSONObject(
                        Json.encodeToString(
                            LeaderSetMsg(action = "SET", recipient = newLeader, reason = reason, votes = maxVotes.toInt())
                        )
                    ), "redivelocity:leader-election")
                }

                RediVelocity.instance.lettuceClient.commands.del("redivelocity:votes")
            }

            delay(15000)
        }
    }
}