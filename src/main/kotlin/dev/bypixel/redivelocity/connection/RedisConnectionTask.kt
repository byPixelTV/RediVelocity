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

package dev.bypixel.redivelocity.connection

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.util.RediVelocityLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object RedisConnectionTask {
    val job = RediVelocityCoroutineScope.launch(Dispatchers.IO) {
        while (isActive) {
            if (!RediVelocity.instance.lettuceClient.connection.isOpen || RediVelocity.instance.lettuceClient.checkConnectionErrors().isNotEmpty()) {
                RediVelocityLogger.warn("Redis connection was lost. Attempting to reconnect...")
                RediVelocity.instance.lettuceClient.reconnectAll()
            }
            delay(5000L)
        }
    }
}