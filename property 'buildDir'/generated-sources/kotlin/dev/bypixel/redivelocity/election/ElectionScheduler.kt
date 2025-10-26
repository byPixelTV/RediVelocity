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

package dev.bypixel.redivelocity.election

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import java.security.SecureRandom

object ElectionScheduler {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        val electionCounter = intArrayOf(0)

        while (isActive) {
            val activeProxies = RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxies").toList()

            if (activeProxies.isEmpty()) {
                // No active proxies, skip election and wait for the next cycle
                continue
            }

            val currentLeader = RediVelocity.instance.lettuceClient.commands.get("redivelocity:leader")
            val forceNewElection = (electionCounter[0]++ % 20 == 0)
            val needNewLeader = currentLeader == null || !activeProxies.contains(currentLeader) || forceNewElection

            if (needNewLeader) {
                val reason = if (currentLeader == null) "No leader found" else (if (!activeProxies.contains(currentLeader)) "Leader $currentLeader is not active" else "Scheduled forced election")

                if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                    RediVelocityLogger.info("Starting leader election. Reason: $reason")
                }

                RediVelocity.instance.lettuceClient.commands.del("redivelocity:leader")

                activeProxies.forEach { voter ->
                    val candidates = activeProxies.toMutableList()

                    if (candidates.size > 1) {
                        candidates.remove(voter)
                    }

                    val candidate = candidates[SecureRandom().nextInt(candidates.size)]

                    RediVelocity.instance.lettuceClient.commands.hset("redivelocity:votes", voter, candidate)
                }

                val allVotes = RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:votes").toList().mapNotNull { kv -> kv.value?.let { value -> kv.key to value } }
                    .toMap()

                val voteCount = allVotes.map { it.key to it.value.toInt() }.toMap()

                if (voteCount.isEmpty()) {
                    val newLeader = activeProxies[SecureRandom().nextInt(activeProxies.size)]

                    RediVelocity.instance.lettuceClient.commands.set("redivelocity:leader", newLeader)

                    if (newLeader == RediVelocity.instance.proxyId) {
                        if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                            RediVelocityLogger.info("This proxy (${RediVelocity.instance.proxyId}) has been elected as leader by random selection (random election).")
                        }
                    }
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

                if (newLeader == RediVelocity.instance.proxyId) {
                    if (RediVelocity.instance.config.getBoolean(Route.fromString("debug-mode"))) {
                        RediVelocityLogger.info("This proxy (${RediVelocity.instance.proxyId}) has been elected as leader with $maxVotes votes.")
                    }
                }
            }

            delay(15000)
        }
    }
}