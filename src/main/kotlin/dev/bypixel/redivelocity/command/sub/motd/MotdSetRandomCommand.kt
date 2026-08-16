package dev.bypixel.redivelocity.command.sub.motd

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMotdMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage

object MotdSetRandomCommand {
    fun motdSetRandomCommand() : CommandAPICommand {
        return CommandAPICommand("set-random")
            .withPermission("redivelocity.command.motd.set-random")
            .executes(CommandExecutor { sender, _ ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        SetMotdMessage("")
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.motd.set-random")))
                    )
                }
            })
    }
}