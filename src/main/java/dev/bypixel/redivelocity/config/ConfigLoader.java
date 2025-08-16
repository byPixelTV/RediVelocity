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

package dev.bypixel.redivelocity.config;

import dev.bypixel.redivelocity.RediVelocityLogger;
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
import java.util.HashSet;
import java.util.Set;

@Singleton
public class ConfigLoader {

    private final RediVelocityLogger rediVelocityLogger;
    private final LoaderOptions loaderOptions = new LoaderOptions();
    private final Yaml yaml = new Yaml(new Constructor(Config.class, loaderOptions));

    @Getter
    private Config config;
    private boolean configUpdated = false;

    @Inject
    public ConfigLoader(RediVelocityLogger rediVelocityLogger) {
        this.rediVelocityLogger = rediVelocityLogger;
        load();
    }

    public void load() {
        File configFile = new File("plugins/redivelocity/config.yml");
        configUpdated = false;

        // Verzeichnis erstellen, falls es nicht existiert
        if (configFile.getParentFile() != null && !configFile.getParentFile().exists()) {
            if (!configFile.getParentFile().mkdirs()) {
                rediVelocityLogger.sendErrorLogs("<red>Config dir could not be created</red>");
            }
        } else if (configFile.getParentFile() == null) {
            rediVelocityLogger.sendErrorLogs("<red>Parent directory was NULL. Please check the path for errors</red>");
            return;
        }

        // Neue Konfiguration erstellen, wenn keine existiert
        if (!configFile.exists()) {
            try {
                rediVelocityLogger.sendConsoleMessage("<yellow>Config not found... Generating a new one</yellow>");
                if (configFile.createNewFile()) {
                    config = new Config(); // Standardkonfiguration
                    save();
                } else {
                    rediVelocityLogger.sendErrorLogs("<red>Config could not be created</red>");
                }
                return;
            } catch (IOException e) {
                rediVelocityLogger.sendErrorLogs("<red>Error while creating config: " + e.getMessage() + "</red>");
            }
        }

        Config loadedConfig;
        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            loadedConfig = yaml.load(inputStream);

            if (loadedConfig == null) {
                rediVelocityLogger.sendErrorLogs("<red>Config is empty or contains errors. Using default...</red>");
                config = new Config();
                configUpdated = true;
            } else {
                Config defaultConfig = new Config();

                if (loadedConfig.getConfigVersion() < defaultConfig.getConfigVersion()) {
                    rediVelocityLogger.sendConsoleMessage("<yellow>Old config detected. Upgrading from v" +
                            loadedConfig.getConfigVersion() + " to v" + defaultConfig.getConfigVersion() + "...</yellow>");
                    backupConfig(configFile);
                    configUpdated = true;
                }

                config = mergeConfigs(defaultConfig, loadedConfig);

                if (loadedConfig.getConfigVersion() < defaultConfig.getConfigVersion()) {
                    config.setConfigVersion(defaultConfig.getConfigVersion());
                    configUpdated = true;
                }
            }
        } catch (Exception e) {
            rediVelocityLogger.sendErrorLogs("<red>Error while loading config: " + e.getMessage() + ". Trying recovery...</red>");
            backupConfig(configFile);
            config = new Config();
            configUpdated = true;
        }

        if (configUpdated) {
            save();
            rediVelocityLogger.sendConsoleMessage("<green>Config got updated and saved</green>");
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter("plugins/redivelocity/config.yml")) {
            writer.write("# RediVelocity config\n");
            writer.write("# Generated automatically at plugin load\n\n");

            writer.write("# Internal config version. DO NOT CHANGE!\n");
            writer.write("configVersion: " + config.getConfigVersion() + "\n");
            writer.write("# Debug mode (verbose logging)\n");
            writer.write("debugMode: " + config.isDebugMode() + "\n\n");

            writer.write("# Json format for some data\n");
            writer.write("jsonFormat: " + config.isJsonFormat() + "\n\n");

            writer.write("# Redis config\n");
            writer.write("redis:\n");
            writer.write("  host: \"" + config.getRedis().getHost() + "\"\n");
            writer.write("  port: " + config.getRedis().getPort() + "\n");
            writer.write("  username: \"" + config.getRedis().getUsername() + "\"\n");
            writer.write("  password: \"" + config.getRedis().getPassword() + "\"\n");
            writer.write("  cluster: " + config.getRedis().isCluster() + "\n");
            writer.write("  channel: \"" + config.getRedis().getChannel() + "\"\n\n");

            writer.write("# Cloud system integration (can be simplecloud, vulpescloud or cloudnet)\n");
            writer.write("cloud:\n");
            writer.write("  enabled: " + config.getCloud().isEnabled() + "\n");
            writer.write("  cloudSystem: \"" + config.getCloud().getCloudSystem() + "\"\n\n");

            writer.write("# Playercount sync\n");
            writer.write("playerCountSync: " + config.isPlayerCountSync() + "\n\n");

            writer.write("# Versioncontroll\n");
            writer.write("versionControl:\n");
            writer.write("  enabled: " + config.getVersionControl().isEnabled() + "\n");
            writer.write("  allowedVersions:\n");
            for (Integer version : config.getVersionControl().getAllowedVersions()) {
                writer.write("    - " + version + "\n");
            }
            writer.write("  kickMessage: \"" + escapeString(config.getVersionControl().getKickMessage()) + "\"\n\n");

            writer.write("# Joingate\n");
            writer.write("joingate:\n");
            writer.write("  allowJavaClients: " + config.getJoingate().getAllowJavaClients() + "\n");
            writer.write("  floodgateHook: " + config.getJoingate().getFloodgateHook() + "\n");
            writer.write("  allowBedrockClients: " + config.getJoingate().getAllowBedrockClients() + "\n\n");

            writer.write("# Message format\n");
            writer.write("messages:\n");
            writer.write("  prefix: \"" + escapeString(config.getMessages().getPrefix()) + "\"\n\n");
        } catch (IOException e) {
            rediVelocityLogger.sendErrorLogs("<red>Config file could not be save: " + e.getMessage() + "</red>");
        }
    }

    private void backupConfig(File configFile) {
        try {
            File backupDir = new File(configFile.getParentFile(), "config-backup");
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                rediVelocityLogger.sendErrorLogs("<red>Config backup directory could not be created</red>");
                return;
            }

            // Eindeutigen Dateinamen mit Zeitstempel erstellen
            String timestamp = String.valueOf(System.currentTimeMillis());
            File backupFile = new File(backupDir, configFile.getName() + "." + timestamp + ".bak");

            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            rediVelocityLogger.sendConsoleMessage("<green>Create backup of config: " + backupFile.getAbsolutePath() + "</green>");
        } catch (IOException ex) {
            rediVelocityLogger.sendErrorLogs("<red>Could not created a config backup: " + ex.getMessage() + "</red>");
        }
    }

    private Config mergeConfigs(Config defaultConfig, Config loadedConfig) {
        if (loadedConfig == null) {
            return defaultConfig;
        }

        Set<String> updatedPaths = new HashSet<>();

        boolean updated = mergeRecursive(defaultConfig, loadedConfig, "root", updatedPaths);

        if (updated) {
            rediVelocityLogger.sendConsoleMessage("<yellow>Missing config entries added.</yellow>");
            configUpdated = true;
        }

        return loadedConfig;
    }

    private boolean mergeRecursive(Object defaultObject, Object loadedObject, String path, Set<String> updatedPaths) {
        if (defaultObject == null || loadedObject == null) {
            return false;
        }

        boolean updated = false;
        Field[] defaultFields = defaultObject.getClass().getDeclaredFields();
        Set<String> loadedFieldNames = new HashSet<>();

        for (Field field : loadedObject.getClass().getDeclaredFields()) {
            loadedFieldNames.add(field.getName());
        }

        for (Field field : defaultFields) {
            try {
                field.setAccessible(true);
                String fieldPath = path.equals("root") ? field.getName() : path + "." + field.getName();

                Object defaultValue = field.get(defaultObject);

                if (!loadedFieldNames.contains(field.getName())) {
                    rediVelocityLogger.sendConsoleMessage("<yellow>Found missing field: " + fieldPath + "</yellow>");
                    field.set(loadedObject, defaultValue);
                    updatedPaths.add(fieldPath);
                    updated = true;
                    continue;
                }

                Object loadedValue = field.get(loadedObject);

                if (loadedValue == null) {
                    field.set(loadedObject, defaultValue);
                    rediVelocityLogger.sendConsoleMessage("<yellow>Null value replace for field: " + fieldPath + "</yellow>");
                    updatedPaths.add(fieldPath);
                    updated = true;
                }
                else if (isConfigObject(field.getType())) {
                    boolean subUpdated = mergeRecursive(defaultValue, loadedValue, fieldPath, updatedPaths);
                    updated = updated || subUpdated;
                }
            } catch (IllegalAccessException | InaccessibleObjectException e) {
                rediVelocityLogger.sendErrorLogs("<red>Error while accessing field: " + field.getName() + " - " + e.getMessage() + "</red>");
            }
        }

        return updated;
    }

    /**
     * Checks if the given class is a configuration object
     */
    private boolean isConfigObject(Class<?> type) {
        return !isPrimitiveOrWrapper(type) &&
                !type.equals(String.class) &&
                !type.isArray() &&
                !type.isEnum() &&
                !type.getPackageName().startsWith("java.");
    }

    private boolean isPrimitiveOrWrapper(Class<?> type) {
        return type.isPrimitive() ||
                type == Integer.class || type == Long.class || type == Short.class ||
                type == Double.class || type == Float.class || type == Boolean.class ||
                type == Byte.class || type == Character.class;
    }

    /**
     * Escapes special characters in YAML strings
     */
    private String escapeString(String input) {
        if (input == null) return "";

        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}