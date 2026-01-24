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

package dev.bypixel.redivelocity.command.sub.proxy

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.cache.ProxyCache
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

object ProxyInfoCommand {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun proxyInfoCommand() : CommandAPICommand {
        return CommandAPICommand("info")
            .withArguments(
                StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                    ProxyCache.getProxies()
                })
            )
            .withPermission("redivelocity.command.proxy.info")
            .executes(CommandExecutor { sender, args ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    if (!ProxyCache.getProxies().contains(args[0] as String)) {
                        sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(
                                RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.proxy.info.proxy_not_found")),
                                Placeholder.unparsed("proxy", args[0] as String),
                                Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                            )
                        )
                        return@launch
                    }

                    val leaderProxy = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.get("redivelocity:leader")
                    }
                        ?: throw IllegalStateException("Leader proxy is not set in Redis.")

                    val proxyPlayerCount = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:proxy:player-counts", args[0] as String)
                            ?: "0"
                    }

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                            """
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.proxy.info.msg"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.proxy.info.id"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.proxy.info.is_leader"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.proxy.info.player_count"))}
                            """.trimIndent(),
                            Placeholder.unparsed("proxy", args[0] as String),
                            Placeholder.parsed("is_leader", if (leaderProxy == args[0] as String) "<#4bfb00>true</#4bfb00>" else "<#dc2626>false</#dc2626>"),
                            Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))),
                            Placeholder.unparsed("player_count", proxyPlayerCount)
                        )
                    )
                }
            })
    }
}