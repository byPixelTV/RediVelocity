package dev.bypixel.redivelocity.command.sub.motd

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMaintenanceMotdMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage

object MotdSetRandomMaintenanceCommand {
    fun motdSetRandomMaintenanceCommand() : CommandAPICommand {
        return CommandAPICommand("set-random-maintenance")
            .withPermission("redivelocity.command.motd.set-random-maintenance")
            .executes(CommandExecutor { sender, _ ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        SetMaintenanceMotdMessage("")
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.motd.set-random-maintenance")))
                    )
                }
            })
    }
}