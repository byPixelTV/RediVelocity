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

package dev.bypixel.redivelocity.feature.globalPlayercount

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.flow.toList

object PlayercountUtil {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun calcGlobalPlayercount() : Long {
        var playercount = 0L

        RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxy:player-counts").toList().forEach {
            playercount += it.toLong()
        }

        return playercount
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getProxyPlayercount(proxyId: String) : Long {
        val count = RediVelocity.instance.lettuceClient.commands.hget("redivelocity:proxy:player-counts", proxyId)
        return count?.toLong() ?: 0L
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getPlayercountMap() : Map<String, Long> {
        val result = mutableMapOf<String, Long>()

        RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:proxy:player-counts")
            .collect { kv ->
                result[kv.key] = kv.value.toLong()
            }

        return result
    }
}