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

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import dev.bypixel.redivelocity.redis.RedisPlayerLimit
import dev.bypixel.redivelocity.util.RediVelocityLogger
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

object PlayercountScheduler {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val globalPlayerCountCalcScheduler = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            PlayercountUtil.calcGlobalPlayercount()

            delay(5000L.milliseconds)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val proxyPlayerCountUpdateScheduler = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            PlayercountUtil.setProxyPlayercount()
            try {
                RedisPlayerLimit.refreshBypass()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RediVelocityLogger.warn("Failed to refresh max-player bypass permissions: ${e.message}")
            }
            delay(2000L.milliseconds)
        }
    }
}
