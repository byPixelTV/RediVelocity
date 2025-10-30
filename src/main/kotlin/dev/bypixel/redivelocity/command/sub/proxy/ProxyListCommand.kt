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

package dev.bypixel.redivelocity.command.sub.proxy

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ProxyListCommand {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun proxyListCommand() : CommandAPICommand {
        return CommandAPICommand("list")
            .withPermission("redivelocity.command.proxy.list")
            .executes(CommandExecutor { sender, args ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    val proxyPlayercountMap = mutableMapOf<String, Int>()

                    RediVelocity.instance.lettuceClient.commands.hgetall("redivelocity:proxy:player-counts").collect { kv ->
                        proxyPlayercountMap[kv.key] = kv.value.toInt()
                    }

                    val proxies = proxyPlayercountMap.keys.toList()


                }
            })
    }
}