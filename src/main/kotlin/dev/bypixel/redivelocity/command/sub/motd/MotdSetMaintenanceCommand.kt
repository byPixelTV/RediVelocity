package dev.bypixel.redivelocity.command.sub.motd

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMotdMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

object MotdSetMaintenanceCommand {
    fun motdSetMaintenanceCommand() : CommandAPICommand {
        return CommandAPICommand("set-maintenance")
            .withPermission("redivelocity.command.motd.set-maintenance")
            .withArguments(
                StringArgument("motd").replaceSuggestions(
                    ArgumentSuggestions.stringCollection {
                        RediVelocity.instance.rawConfig
                            .getSection(
                                Route.fromString("login-configuration.motds")
                            )
                            ?.getRoutesAsStrings(false)
                            ?.toSet()
                            ?: emptySet()
                    }
                )
            )
            .executes(CommandExecutor { sender, args ->
                val motd = args[0] as String
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        SetMotdMessage(motd)
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString("commands.redivelocity.motd.set-maintenance")), Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))), Placeholder.unparsed("motd", motd))
                    )
                }
            })
    }
}