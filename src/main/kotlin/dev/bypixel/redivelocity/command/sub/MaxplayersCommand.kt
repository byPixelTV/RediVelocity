package dev.bypixel.redivelocity.command.sub

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMaxPlayersMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

object MaxplayersCommand {
    fun maxplayersCommand() : CommandAPICommand {
        return CommandAPICommand("max-players")
            .withPermission("redivelocity.command.player.max-players")
            .withArguments(
                IntegerArgument("max-players", -1)
            )
            .executes(CommandExecutor { sender, args ->
                val count = args[0] as Int
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        SetMaxPlayersMessage(count)
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString( "commands.redivelocity.max-players.set")), Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))),
                            Placeholder.parsed("max_players", count.toString()))
                    )
                }
            })
    }
}