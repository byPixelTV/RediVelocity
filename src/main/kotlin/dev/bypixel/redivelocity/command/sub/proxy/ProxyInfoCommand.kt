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
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.future

object ProxyInfoCommand {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun proxyInfoCommand() : CommandAPICommand {
        return CommandAPICommand("info")
            .withArguments(
                StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                    RediVelocityCoroutineScope.future(Dispatchers.IO) {
                        RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxies").toList()
                    }
                })
            )
            .withPermission("redivelocity.command.proxy.info")
            .executes(CommandExecutor { sender, args ->

            })
    }
}