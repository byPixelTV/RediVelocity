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

package dev.bypixel.redivelocity.util

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.flow.toList

object ProxyIdGenerator {
    private const val CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private const val ID_LENGTH = 8
    private const val PROXIES_KEY = "redivelocity:proxies"

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun generate(): String {
        val proxies = RediVelocity.instance.lettuceClient.commands.hvals(PROXIES_KEY).toList()

        var id: String
        do {
            id = generateRandomString()
        } while (proxies.contains(id))

        return "proxy-$id"
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getExistingIds(): List<String> {
        return RediVelocity.instance.lettuceClient.commands.hvals(PROXIES_KEY).toList()
    }

    fun generateRandomString(): String {
        return (1..ID_LENGTH)
            .map { CHARS.random() }
            .joinToString("")
    }
}