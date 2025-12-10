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

import dev.bypixel.redivelocity.command.sub.player.PlayerCommand
import dev.bypixel.redivelocity.command.sub.proxy.ProxyCommand
import dev.jorel.commandapi.CommandAPICommand

class RediVelocityCommand {
    fun register() {
        CommandAPICommand("redivelocity")
            .withAliases("rv", "rediv")
            .withSubcommands(
                PlayerCommand.playerCommand(),
                ProxyCommand.proxyCommand()
            ).register()
    }
}