package de.bypixeltv.redivelocity.config;

import de.bypixeltv.redivelocity.RediVelocityLogger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Singleton
public class ConfigLoader {

    private final RediVelocityLogger rediVelocityLogger;
    private final LoaderOptions loaderOptions = new LoaderOptions();
    private final Yaml yaml = new Yaml(new Constructor(Config.class, loaderOptions));
    @Getter
    private Config config;

    @Inject
    public ConfigLoader(RediVelocityLogger rediVelocityLogger) {
        this.rediVelocityLogger = rediVelocityLogger;
        load();
    }

    public void load() {
        File configFile = new File("plugins/redivelocity/config.yml");

        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            if (!configFile.getParentFile().mkdirs()) {
                rediVelocityLogger.sendErrorLogs("<red>Failed to create configuration directory!</red>");
            }
        } else if (configFile.getParentFile() == null) {
            rediVelocityLogger.sendErrorLogs("<red>Parent directory of configuration file is null! Please check the configuration file path.</red>");
            return;
        }

        if (!configFile.exists()) {
            try {
                rediVelocityLogger.sendConsoleMessage("<red>Configuration file not found! Creating a new one...</red>");
                if (configFile.createNewFile()) {
                    config = new Config(); // Default configuration
                    save();
                } else {
                    rediVelocityLogger.sendErrorLogs("<red>Failed to create configuration file!</red>");
                }
                return;
            } catch (IOException e) {
                rediVelocityLogger.sendErrorLogs("<red>Failed to create configuration file!</red>");
            }
        }

        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            config = yaml.load(inputStream);
            if (config == null) {
                rediVelocityLogger.sendErrorLogs("<red>Config file is empty or malformed! Using default configuration.</red>");
                config = new Config(); // Fallback to default config
            }
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("<red>Failed to load configuration. Attempting recovery...</red>");
            backupConfig(configFile);
            config = new Config(); // Load plugin defaults as fallback
        }

        // Merge missing keys from default configuration and log
        Config defaultConfig = new Config();
        config = mergeConfigs(defaultConfig, config);

        // Save updated configuration to file
        save();
    }

    public void save() {
        try (FileWriter writer = new FileWriter("plugins/redivelocity/config.yml")) {
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
            writer.write("  enabled: " + config.getCloud().isEnabled() + "\n");
            writer.write("  cloudSystem: " + config.getCloud().getCloudSystem() + "\n");

            writer.write("# Player Count Sync\n");
            writer.write("playerCountSync: " + config.isPlayerCountSync() + "\n");

            writer.write("# Version control\n");
            writer.write("versionControl:\n");
            writer.write("  enabled: " + config.getVersionControl().isEnabled() + "\n");
            writer.write("  allowedVersions:\n");
            for (Integer version : config.getVersionControl().getAllowedVersions()) {
                writer.write("    - " + version + "\n");
            }
            writer.write("  kickMessage: " + config.getVersionControl().getKickMessage() + "\n");

            writer.write("# Joingate\n");
            writer.write("joingate:\n");
            writer.write("  allowJavaClients: " + config.getJoingate().getAllowJavaClients() + "\n");
            writer.write("  floodgateHook: " + config.getJoingate().getFloodgateHook() + "\n");
            writer.write("  allowBedrockClients: " + config.getJoingate().getAllowBedrockClients() + "\n");

            writer.write("# Messages\n");
            writer.write("messages:\n");
            writer.write("  prefix: " + config.getMessages().getPrefix() + "\n");

            writer.write("# Resourcepack\n");
            writer.write("resourcepack:\n");
            writer.write("  enabled: " + config.getResourcepack().isEnabled() + "\n");
            writer.write("  forceResourcepack: " + config.getResourcepack().isForceResourcepack() + "\n");
            writer.write("  resourcepackUrl: " + config.getResourcepack().getResourcepackUrl() + "\n");
            writer.write("  resourcepackMessage: " + config.getResourcepack().getResourcepackMessage() + "\n");
            writer.write("  resourcepackKickMessage: " + config.getResourcepack().getResourcepackKickMessage() + "\n");

        } catch (IOException e) {
            rediVelocityLogger.sendErrorLogs("<red>Failed to save configuration file!</red>");
        }
    }

    private void backupConfig(File configFile) {
        try {
            File backupDir = new File(configFile.getParentFile(), "config-backup");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                rediVelocityLogger.sendErrorLogs("<red>Failed to create backup directory!</red>");
                return;
            }
            File backupFile = new File(backupDir, configFile.getName() + ".bak");
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            rediVelocityLogger.sendConsoleMessage("<green>Created backup of config file at location: " + backupFile.getAbsolutePath() + "</green>");
        } catch (IOException ex) {
            rediVelocityLogger.sendErrorLogs("<red>Failed to create backup of config file!</red>");
        }
    }

    private Config mergeConfigs(Config defaultConfig, Config loadedConfig) {
        if (loadedConfig == null) {
            rediVelocityLogger.sendConsoleMessage("<green>Loaded fallback default configuration.</green>");
            return defaultConfig;
        }

        // Merge and log missing configurations dynamically
        mergeRecursive(defaultConfig, loadedConfig, "root");

        return loadedConfig;
    }

    private void mergeRecursive(Object defaultObject, Object loadedObject, String path) {
        if (defaultObject == null || loadedObject == null) {
            rediVelocityLogger.sendConsoleMessage("<red>Default or loaded object is null at path: " + path + "</red>");
            return;
        }

        Field[] fields = defaultObject.getClass().getDeclaredFields();

        for (Field field : fields) {
            try {
                field.setAccessible(true);

                Object defaultValue = field.get(defaultObject);
                Object loadedValue = field.get(loadedObject);

                if (loadedValue == null) {
                    field.set(loadedObject, defaultValue);
                } else if (isConfigObject(field.getType())) {
                    mergeRecursive(defaultValue, loadedValue, path + "." + field.getName());
                }
            } catch (IllegalAccessException | InaccessibleObjectException e) {
                String fullPath = path + "." + field.getName();
                rediVelocityLogger.sendErrorLogs("<red>Failed to access or merge key: " + fullPath + " due to restricted access.</red>");
            }
        }
    }

    /**
     * Check if the type is a custom Config object (not primitive, wrapper, or string).
     */
    private boolean isConfigObject(Class<?> type) {
        return !isPrimitiveOrWrapper(type) && !type.equals(String.class) && !type.getPackageName().startsWith("java.");
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Integer.class || type == Long.class || type == Short.class ||
                type == Double.class || type == Float.class || type == Boolean.class ||
                type == Byte.class || type == Character.class;
    }
}