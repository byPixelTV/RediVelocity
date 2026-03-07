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

package dev.bypixel.redivelocity.command

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

class FindCommand {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun register() {
        CommandAPICommand("find")
            .withAliases("whereis")
            .withPermission("redivelocity.command.find")
            .withArguments(
                StringArgument("player").replaceSuggestions(ArgumentSuggestions.stringCollection {
                    PlayerCache.getPlayers().values.toList()
                })
            )
            .executes(CommandExecutor { sender, args ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    val playerName = args[0] as String
                    val playerUuid = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hgetall("redivelocity:player:names")
                    }
                        .toList()
                        .firstOrNull { it.value == playerName }?.key

                    if (playerUuid == null) {
                        sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(
                                RediVelocity.instance.messageConfig.getString(Route.fromString("commands.find.player_not_found")),
                                Placeholder.unparsed("player", playerName),
                                Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                            )
                        )
                        return@launch
                    }

                    val currentProxy = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:player:proxies", playerUuid)
                    }
                        ?: run {
                            sender.sendMessage(
                                MiniMessage.miniMessage().deserialize(
                                    RediVelocity.instance.messageConfig.getString(Route.fromString("commands.find.player_not_found")),
                                    Placeholder.unparsed("player", playerName),
                                    Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                                )
                            )
                            return@launch
                        }

                    val currentServer = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:player:servers", playerUuid)
                    }
                        ?: "Unknown"

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                            RediVelocity.instance.messageConfig.getString(Route.fromString("commands.find.player_info")),
                            Placeholder.unparsed("player", playerName),
                            Placeholder.unparsed("proxy", currentProxy),
                            Placeholder.unparsed("server", currentServer),
                            Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                        )
                    )
                }
            }).register()
    }
}