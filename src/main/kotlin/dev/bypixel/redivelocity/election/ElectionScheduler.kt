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

import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.redis.RedisLifecycle
import dev.bypixel.redivelocity.util.RediVelocityLogger
import kotlinx.coroutines.*

object ElectionScheduler {
    // Retain a healthy leader. Re-elect atomically when its heartbeat expires.
    val job = RediVelocityCoroutineScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        while (isActive) {
            try {
                RedisLifecycle.elect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RediVelocityLogger.warn("Leader election cycle failed: ${e.message}")
            }
            delay(15_000L)
        }
    }
}
