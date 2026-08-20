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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.election

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.model.LeaderRemovedMsg
import dev.bypixel.redivelocity.model.LeaderSetMsg
import dev.bypixel.redivelocity.model.LeaderVoteMsg
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.dejvokep.boostedyaml.route.Route
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.time.Duration.Companion.milliseconds

object ElectionScheduler {

    private const val ELECTION_INTERVAL_MS = 15_000L
    private const val LEADER_HEARTBEAT_TIMEOUT_MS = 30_000L

    private const val LEADER_KEY = "redivelocity:leader"
    private const val LEADER_FIELD = "leader-id"

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        var electionCounter = 0

        delay(ELECTION_INTERVAL_MS.milliseconds)

        while (isActive) {
            try {
                val activeProxies =
                    RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                        redis.hvals("redivelocity:proxies")
                            .toList()
                    }

                if (activeProxies.isEmpty()) {
                    delay(ELECTION_INTERVAL_MS.milliseconds)
                    continue
                }

                var currentLeader =
                    RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                        redis.hget(
                            LEADER_KEY,
                            LEADER_FIELD
                        )
                    }

                if (currentLeader == null) {
                    val newLeader =
                        activeProxies[
                            SecureRandom().nextInt(activeProxies.size)
                        ]

                    RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                        redis.hset(
                            LEADER_KEY,
                            LEADER_FIELD,
                            newLeader
                        )
                    }

                    currentLeader = newLeader

                    if (
                        RediVelocity.instance.config.getBoolean(
                            Route.fromString("debug-mode")
                        )
                    ) {
                        RediVelocityLogger.info(
                            "No leader existed. Assigned $newLeader as leader."
                        )
                    }
                } else {
                    val leaderHeartbeat =
                        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                            redis.hget(
                                "redivelocity:heartbeats",
                                currentLeader
                            )
                        }?.toLongOrNull()

                    if (
                        leaderHeartbeat == null ||
                        System.currentTimeMillis() - leaderHeartbeat >
                        LEADER_HEARTBEAT_TIMEOUT_MS
                    ) {
                        if (
                            RediVelocity.instance.config.getBoolean(
                                Route.fromString("debug-mode")
                            )
                        ) {
                            RediVelocityLogger.info(
                                "Removing stale leader $currentLeader due to missing/stale heartbeat."
                            )
                        }

                        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                            val latestLeader = redis.hget(
                                LEADER_KEY,
                                LEADER_FIELD
                            )

                            if (latestLeader == currentLeader) {
                                redis.hdel(
                                    LEADER_KEY,
                                    LEADER_FIELD
                                )
                            }
                        }

                        delay(ELECTION_INTERVAL_MS.milliseconds)
                        continue
                    }
                }

                if (currentLeader != RediVelocity.instance.proxyId) {
                    delay(ELECTION_INTERVAL_MS.milliseconds)
                    continue
                }

                electionCounter++

                val forceNewElection =
                    electionCounter % 20 == 0

                val needNewLeader =
                    !activeProxies.contains(currentLeader) ||
                            forceNewElection

                if (needNewLeader) {
                    val reason =
                        if (!activeProxies.contains(currentLeader)) {
                            "Leader $currentLeader is not active"
                        } else {
                            "Scheduled forced election"
                        }

                    if (
                        RediVelocity.instance.config.getBoolean(
                            Route.fromString("debug-mode")
                        )
                    ) {
                        RediVelocityLogger.info(
                            "Starting leader election. Reason: $reason"
                        )
                    }

                    RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                        redis.del(
                            "redivelocity:votes"
                        )
                    }

                    RediVelocity.instance.lettuceClient.sendMessage(
                        JSONObject(
                            Json.encodeToString(
                                LeaderVoteMsg(
                                    action = "VOTE_REQUEST"
                                )
                            )
                        ),
                        "redivelocity:leader-election"
                    )

                    delay(2500.milliseconds)

                    val allVotes =
                        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                            redis.hgetall(
                                "redivelocity:votes"
                            )
                                .toList()
                                .mapNotNull { kv ->
                                    val value =
                                        kv.value.toIntOrNull()
                                            ?: return@mapNotNull null

                                    kv.key to value
                                }
                                .toMap()
                        }

                    val newLeader =
                        if (allVotes.isEmpty()) {
                            activeProxies[
                                SecureRandom().nextInt(
                                    activeProxies.size
                                )
                            ]
                        } else {
                            val maxVotes =
                                allVotes.values.maxOrNull() ?: 0

                            val topCandidates =
                                allVotes
                                    .filterValues {
                                        it == maxVotes
                                    }
                                    .keys
                                    .filter {
                                        it in activeProxies
                                    }

                            if (topCandidates.isEmpty()) {
                                activeProxies[
                                    SecureRandom().nextInt(
                                        activeProxies.size
                                    )
                                ]
                            } else {
                                topCandidates[
                                    SecureRandom().nextInt(
                                        topCandidates.size
                                    )
                                ]
                            }
                        }

                    val previousLeader =
                        RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                            val latestLeader =
                                redis.hget(
                                    LEADER_KEY,
                                    LEADER_FIELD
                                )

                            if (
                                latestLeader !=
                                RediVelocity.instance.proxyId
                            ) {
                                return@withCoroutines null
                            }

                            redis.hset(
                                LEADER_KEY,
                                LEADER_FIELD,
                                newLeader
                            )

                            latestLeader
                        }

                    if (previousLeader == null) {
                        delay(ELECTION_INTERVAL_MS.milliseconds)
                        continue
                    }

                    if (newLeader != previousLeader) {
                        RediVelocity.instance.lettuceClient.sendMessage(
                            JSONObject(
                                Json.encodeToString(
                                    LeaderRemovedMsg(
                                        action = "REMOVED",
                                        recipient = previousLeader
                                    )
                                )
                            ),
                            "redivelocity:leader-election"
                        )
                    }

                    val votesForWinner =
                        allVotes[newLeader] ?: 0

                    if (
                        newLeader ==
                        RediVelocity.instance.proxyId
                    ) {
                        if (
                            RediVelocity.instance.config.getBoolean(
                                Route.fromString("debug-mode")
                            )
                        ) {
                            RediVelocityLogger.info(
                                "This proxy (${RediVelocity.instance.proxyId}) " +
                                        "has been elected as leader with " +
                                        "$votesForWinner votes."
                            )
                        }
                    } else {
                        RediVelocity.instance.lettuceClient.sendMessage(
                            JSONObject(
                                Json.encodeToString(
                                    LeaderSetMsg(
                                        action = "SET",
                                        recipient = newLeader,
                                        reason = if (allVotes.isEmpty()) {
                                            "Random election due to no votes"
                                        } else {
                                            reason
                                        },
                                        votes = votesForWinner
                                    )
                                )
                            ),
                            "redivelocity:leader-election"
                        )
                    }

                    RediVelocity.instance.lettuceClient.withCoroutines { redis ->
                        redis.del(
                            "redivelocity:votes"
                        )
                    }
                }
            } catch (t: Throwable) {
                RediVelocityLogger.warn(
                    "Leader election cycle failed: ${t.message}"
                )
            }

            delay(ELECTION_INTERVAL_MS.milliseconds)
        }
    }
}