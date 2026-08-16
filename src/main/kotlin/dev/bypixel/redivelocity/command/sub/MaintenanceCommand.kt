package dev.bypixel.redivelocity.command.sub

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMaintenanceMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.BooleanArgument
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

object MaintenanceCommand {
    fun maintenanceCommand() : CommandAPICommand {
        return CommandAPICommand("maintenance")
            .withPermission("redivelocity.command.player.maintenance")
            .withArguments(
                BooleanArgument("state")
            )
            .executes(CommandExecutor { sender, args ->
                val state = args[0] as Boolean
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        SetMaintenanceMessage(state)
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString(if (state) "commands.redivelocity.maintenance.enable" else "commands.redivelocity.maintenance.disable")), Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))))
                    )
                }
            })
    }
}