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

package dev.bypixel.redivelocity.command.sub.player

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

object PlayerInfoCommand {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    fun playerInfoCommand() : CommandAPICommand {
        return CommandAPICommand("info")
            .withArguments(
                StringArgument("player").replaceSuggestions(ArgumentSuggestions.stringCollection {
                    PlayerCache.getPlayers().values.toList()
                })
            )
            .withPermission("redivelocity.command.player.info")
            .executes(CommandExecutor { sender, args ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    val playerName = args[0] as String
                    val playerUuid = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hgetall("redivelocity:player:names")
                    }.toList().firstOrNull { it.value == playerName }?.key

                    if (playerUuid == null) {
                        sender.sendMessage(
                            MiniMessage.miniMessage().deserialize(
                                RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.general.not_found")),
                                Placeholder.unparsed("player", playerName),
                                Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                            )
                        )
                        return@launch
                    }

                    val playerIp = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:player:ips",
                            playerUuid
                        )
                    }
                    val playerServer = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:player:servers", playerUuid)
                    }
                    val playerProxy = RediVelocity.instance.lettuceClient.withCoroutines {
                        it.hget("redivelocity:proxy:players", playerUuid)
                    }

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(
                            """
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.info.msg"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.info.uuid"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.info.address"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.info.proxy"))}
                                ${RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.player.info.server"))}
                            """.trimIndent(),
                            Placeholder.unparsed("player", playerName),
                            Placeholder.unparsed("uuid", playerUuid),
                            Placeholder.unparsed("ip", playerIp ?: "Unknown"),
                            Placeholder.unparsed("server", playerServer ?: "Unknown"),
                            Placeholder.unparsed("proxy", playerProxy ?: "Unknown"),
                            Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix")))
                        )
                    )
                }
            })
    }
}