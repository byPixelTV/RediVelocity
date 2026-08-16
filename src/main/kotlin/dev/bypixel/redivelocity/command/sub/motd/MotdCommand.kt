package dev.bypixel.redivelocity.command.sub.motd

import dev.jorel.commandapi.CommandAPICommand

object MotdCommand {
    fun motdCommand() : CommandAPICommand {
        return CommandAPICommand("motd")
            .withSubcommands(
                MotdSetCommand.motdSetCommand(),
                MotdSetMaintenanceCommand.motdSetMaintenanceCommand(),
                MotdSetRandomCommand.motdSetRandomCommand(),
                MotdSetRandomMaintenanceCommand.motdSetRandomMaintenanceCommand()
            )
    }
}