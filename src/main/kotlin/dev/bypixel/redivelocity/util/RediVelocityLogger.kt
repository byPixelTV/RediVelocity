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

package dev.bypixel.redivelocity.util

import dev.bypixel.redivelocity.RediVelocity
import net.kyori.adventure.text.minimessage.MiniMessage

object RediVelocityLogger {
    private val mm = MiniMessage.miniMessage()

    fun getCleanCallingClassName(): String {
        val stackTrace = Thread.currentThread().stackTrace

        // Find the first meaningful caller class
        for (i in 3 until stackTrace.size) { // Start from index 3 to skip the current method and the caller method
            val className = stackTrace[i].className
            if (!className.startsWith("java.") &&
                !className.startsWith("com.velocitypowered.") &&
                !className.startsWith("io.papermc.") &&
                !className.startsWith("org.bukkit.") &&
                !className.startsWith("org.spigot.") &&
                !className.startsWith("net.minecraft.server.") &&
                !className.contains("$$") && // Remove dynamically generated classes
                !className.contains("Lambda")) {

                // Return a clean simple class name
                return className.substring(className.lastIndexOf('.') + 1)
            }
        }

        return "UnknownSource" // Fallback if no valid caller is found
    }

    fun info(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <grey><color:#4bfb00>[INFO]</color> <color:#fede00>[$className]</color> $message</grey>"))
    }

    fun error(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <color:#ff0000>[ERROR] <color:#fede00>[$className]</color> $message</color>"))
    }

    fun warn(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <color:#ffa500>[WARN] <color:#fede00>[$className]</color> $message</color>"))
    }

    fun debug(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <color:#7F00FF>[DEBUG] <color:#fede00>[$className]</color> $message</color>"))
    }

    fun success(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <color:#4bfb00>[SUCCESS] <color:#fede00>[$className]</color> $message</color>"))
    }

    fun consoleMessage(message: String) {
        val className = getCleanCallingClassName()

        RediVelocity.server.consoleCommandSource.sendMessage(mm.deserialize("<dark_grey>[<aqua>RediVelocity</aqua>]</dark_grey> <grey><color:#fede00>[$className]</color> $message</grey>"))
    }

    fun counter(): () -> Unit {
        var count = 0
        return {
            debug("Log Statement ${count++}")
        }
    }
}