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

package dev.bypixel.redivelocity.feature.globalPlayercount

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

object PlayercountUtil {
    var globalPlayercountCache = 0L

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun calcGlobalPlayercount() : Long = withContext(Dispatchers.IO) {

        val playercount = RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxy:players").toList().size.toLong()

        globalPlayercountCache = 0L

        globalPlayercountCache = playercount

        playercount
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun setProxyPlayercount() = withContext(Dispatchers.IO) {
        RediVelocity.instance.lettuceClient.commands.hset("redivelocity:proxy:player-counts", RediVelocity.instance.proxyId, RediVelocity.instance.proxy.allPlayers.size.toString())
    }
}