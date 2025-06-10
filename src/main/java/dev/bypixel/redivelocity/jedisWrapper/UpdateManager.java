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

package dev.bypixel.redivelocity.jedisWrapper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.bypixel.redivelocity.RediVelocityLogger;
import dev.bypixel.redivelocity.utils.Version;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.function.Consumer;

@Singleton
public class UpdateManager {

    private final RediVelocityLogger rediVelocityLogger;
    private final ProxyServer proxy;

    @Inject
    public UpdateManager(RediVelocityLogger rediVelocityLogger, ProxyServer proxy) {
        this.rediVelocityLogger = rediVelocityLogger;
        this.proxy = proxy;
    }

    private RediVelocityLogger getRediVelocity() {
        return rediVelocityLogger;
    }

    public void checkForUpdate() {
        getRediVelocity().sendLogs("Checking for updates...");
        getLatestReleaseVersion(version -> {
            Optional<?> pluginOptional = proxy.getPluginManager().getPlugin("redivelocity");
            if (pluginOptional.isPresent()) {
                PluginContainer plugin = (PluginContainer) pluginOptional.get();
                String currentVersionString = plugin.getDescription().getVersion().orElse("0"); // Default to "0" if not present
                try {
                    Version currentVersion = Version.fromString(currentVersionString);
                    Version latestVersion = Version.fromString(version);
                    // Compare versions and notify accordingly
                    if (latestVersion.compareTo(currentVersion) <= 0) {
                        getRediVelocity().sendConsoleMessage("<green>The plugin is up to date!</green>");
                    } else {
                        getRediVelocity().sendConsoleMessage("<red>The plugin is not up to date!</red>");
                        getRediVelocity().sendConsoleMessage(" - Current version: <red>v" + currentVersionString + "</red>");
                        getRediVelocity().sendConsoleMessage(" - Available update: <green>v" + version + "</green>");
                        getRediVelocity().sendConsoleMessage(" - Download available at: <aqua>https://github.com/byPixelTV/RediVelocity/releases</aqua>");
                    }
                } catch (NumberFormatException e) {
                    getRediVelocity().sendErrorLogs("Invalid version format: " + currentVersionString);
                }
            } else {
                getRediVelocity().sendErrorLogs("Plugin 'redivelocity' not found.");
            }
        });
    }

    private void getLatestReleaseVersion(Consumer<String> consumer) {
        try {
            URI url = new URI("https://api.github.com/repos/byPixelTV/RediVelocity/releases/latest");
            BufferedReader reader = new BufferedReader(new InputStreamReader(url.toURL().openStream()));
            JsonObject jsonObject = new Gson().fromJson(reader, JsonObject.class);
            String tagName = jsonObject.get("tag_name").getAsString().replace("v", "");
            consumer.accept(tagName);
        } catch (IOException | URISyntaxException e) {
            getRediVelocity().sendErrorLogs("Checking for updates failed!");
        }
    }
}