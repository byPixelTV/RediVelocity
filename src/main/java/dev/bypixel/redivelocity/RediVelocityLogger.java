/*
 * Copyright (c) 2025.
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

package dev.bypixel.redivelocity;

import com.velocitypowered.api.proxy.ProxyServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;

@Singleton
public class RediVelocityLogger {
    private final ProxyServer proxy;
    private final MiniMessage miniMessages = MiniMessage.miniMessage();

    @Inject
    public RediVelocityLogger(ProxyServer proxy) {
        this.proxy = proxy;
    }

    /**
     * Retrieves a clean, human-readable class name of the logger's caller.
     */
    private String getCleanCallingClassName() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();

        // Find the first meaningful caller class
        for (int i = 3; i < stackTrace.length; i++) { // Start from index 3 to skip the current method and the caller method
            String className = stackTrace[i].getClassName();
            if (!className.startsWith("java.") &&
                    !className.startsWith("com.velocitypowered.") &&
                    !className.contains("$$") && // Remove dynamically generated classes
                    !className.contains("Lambda")) {

                // Return a clean simple class name
                return className.substring(className.lastIndexOf('.') + 1);
            }
        }

        return "UnknownSource"; // Fallback if no valid caller is found
    }


    public void sendLogs(String message) {
        String className = getCleanCallingClassName(); // Clean calling class name
        this.proxy.getConsoleCommandSource().sendMessage(
                miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <yellow>[" + className + "] " + message + "</yellow>")
        );
    }

    public void sendErrorLogs(String message) {
        String className = getCleanCallingClassName(); // Clean calling class name
        this.proxy.getConsoleCommandSource().sendMessage(
                miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>[" + className + "] " + message + "</red>")
        );
    }

    public void sendConsoleMessage(String message) {
        String className = getCleanCallingClassName(); // Clean calling class name
        this.proxy.getConsoleCommandSource().sendMessage(
                miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <yellow>[" + className + "]</yellow> " + message)
        );
    }
}