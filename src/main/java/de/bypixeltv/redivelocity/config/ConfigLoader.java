package de.bypixeltv.redivelocity.config;

import jakarta.inject.Inject;
import lombok.Getter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ConfigLoader {

    private final String configFilePath;
    private final LoaderOptions loaderOptions = new LoaderOptions();
    private final Yaml yaml = new Yaml(new Constructor(Config.class, loaderOptions));
    @Getter
    private Config config;

    @Inject
    public ConfigLoader(String configFilePath) {
        this.configFilePath = configFilePath;
        load();
    }

    public void load() {
        File configFile = new File(configFilePath);
        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                config = new Config();
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try (FileInputStream inputStream = new FileInputStream(configFile)) {
                config = yaml.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // Check if local config version is not set, is lower or higher than the plugin's config version
            Config pluginConfig = new Config();
            assert config != null;
            if (config.getConfigVersion() != pluginConfig.getConfigVersion()) {
                // Create backup directory and copy local config file
                File backupDir = new File(configFile.getParentFile(), "config-backup");
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }
                File backupFile = new File(backupDir, configFile.getName());
                try {
                    Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Save plugin's config to local config file
                config = pluginConfig;
                save();
            }
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(configFilePath)) {
            writer.write("# This is the internal version of the config, DO NOT MODIFY THIS VALUE\n");
            writer.write("configVersion: " + config.getConfigVersion() + "\n");

            writer.write("# Redis configuration\n");
            writer.write("redis:\n");
            writer.write("  host: " + config.getRedis().getHost() + "\n");
            writer.write("  port: " + config.getRedis().getPort() + "\n");
            writer.write("  username: " + config.getRedis().getUsername() + "\n");
            writer.write("  password: " + config.getRedis().getPassword() + "\n");
            writer.write("  useSsl: " + config.getRedis().isUseSsl() + "\n");
            writer.write("  channel: " + config.getRedis().getChannel() + "\n");

            writer.write("# Cloud System hook\n");
            writer.write("cloud:\n");
            writer.write("  # Here you can enable or disable the hook into CloudNet or SimpleCloud, it will get the Proxy id from the cloud and not from our generator\n");
            writer.write("  enabled: " + config.getCloud().isEnabled() + "\n");
            writer.write("  # Here you can set the cloud system, this can be cloudnet or simplecloud\n");
            writer.write("  cloudSystem: " + config.getCloud().getCloudSystem() + "\n");

            writer.write("# Here you can enable or disable the player count sync\n");
            writer.write("playerCountSync: " + config.isPlayerCountSync() + "\n");

            writer.write("# Version control\n");
            writer.write("versionControl:\n");
            writer.write("  # Here you can enable or disable to check the players version\n");
            writer.write("  enabled: " + config.getVersionControl().isEnabled() + "\n");
            writer.write("  allowedVersions:\n");
            for (Integer version : config.getVersionControl().getAllowedVersions()) {
                writer.write("    - " + version + "\n");
            }
            writer.write("  kickMessage: " + config.getVersionControl().getKickMessage() + "\n");

            writer.write("# Joingate\n");
            writer.write("joingate:\n");
            writer.write("  # Here you can enable or disable that Java edition clients can connect\n");
            writer.write("  allowJavaClients: " + config.getJoingate().getAllowJavaClients() + "\n");
            writer.write("  # Here you can enable or disable the Floodgate and GeyserMC hook, both have to be installed on your proxy\n");
            writer.write("  floodgateHook: " + config.getJoingate().getFloodgateHook() + "\n");
            writer.write("  # Here you can enable or disable that Bedrock edition clients can connect (floodgate + geyser have to be installed + support has to be enabled here)\n");
            writer.write("  allowBedrockClients: " + config.getJoingate().getAllowBedrockClients() + "\n");

            writer.write("# Messages\n");
            writer.write("messages:\n");
            writer.write("  # Here you can set the prefix for RediVelocity. For colorcodes you have to use minimessages\n");
            writer.write("  prefix: " + config.getMessages().getPrefix() + "\n");

            writer.write("# Here you can enable or disable the resourcepack sending over the proxy\n# Not yet implemented, will be implemented in the next update or smth\n");
            writer.write("resourcepack:\n");
            writer.write("  enabled: " + config.getResourcepack().isEnabled() + "\n");
            writer.write("  # Here you can enable or disable the force of the resourcepack\n");
            writer.write("  forceResourcepack: " + config.getResourcepack().isForceResourcepack() + "\n");
            writer.write("  # Here you can put the URL of the resourcepack\n");
            writer.write("  resourcepackUrl: " + config.getResourcepack().getResourcepackUrl() + "\n");
            writer.write("  # Here you can put the message for the resourcepack. For colorcodes you have to use minimessages\n");
            writer.write("  resourcepackMessage: " + config.getResourcepack().getResourcepackMessage() + "\n");
            writer.write("  # Here you can put the message for the kick message if the player doesn't accept the resourcepack. For colorcodes you have to use minimessages\n");
            writer.write("  resourcepackKickMessage: " + config.getResourcepack().getResourcepackKickMessage() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}