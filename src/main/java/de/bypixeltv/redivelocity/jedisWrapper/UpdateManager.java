package de.bypixeltv.redivelocity.jedisWrapper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;

import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.utils.Version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.function.Consumer;

@Singleton
public class UpdateManager {

    private final Provider<RediVelocity> rediVelocityProvider;
    private final ProxyServer proxy;
    private final MiniMessage miniMessages = MiniMessage.miniMessage();

    @Inject
    public UpdateManager(Provider<RediVelocity> rediVelocityProvider, ProxyServer proxy) {
        this.rediVelocityProvider = rediVelocityProvider;
        this.proxy = proxy;
    }

    private RediVelocity getRediVelocity() {
        return rediVelocityProvider.get();
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
                        proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <green>The plugin is up to date!</green>"));
                    } else {
                        proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>The plugin is not up to date!</red>"));
                        proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize(" - Current version: <red>v" + currentVersionString + "</red>"));
                        proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize(" - Available update: <green>v" + version + "</green>"));
                        proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize(" - Download available at: <aqua>https://github.com/byPixelTV/RediVelocity/releases</aqua>"));
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