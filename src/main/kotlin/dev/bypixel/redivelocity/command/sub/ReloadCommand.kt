package dev.bypixel.redivelocity.command.sub

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.ConfigReloadMessage
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

object ReloadCommand {
    fun reloadCommand() : CommandAPICommand {
        return CommandAPICommand("reload")
            .withPermission("redivelocity.command.reload")
            .executes(CommandExecutor { sender, _ ->
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    RediVelocity.instance.lettuceClient.sendLettuceMessage(
                        ConfigReloadMessage(true)
                    )

                    sender.sendMessage(
                        MiniMessage.miniMessage().deserialize(RediVelocity.instance.messageConfig.getString(Route.fromString("config-reloaded")), Placeholder.parsed("prefix", RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))))
                    )
                }
            })
    }
}