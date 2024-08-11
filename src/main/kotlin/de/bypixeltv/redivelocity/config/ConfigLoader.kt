package de.bypixeltv.redivelocity.config

import jakarta.inject.Inject
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.io.IOException

class ConfigLoader @Inject constructor(private val configFilePath: String) {

    private val loaderOptions = LoaderOptions()
    private val yaml = Yaml(Constructor(Config::class.java, loaderOptions))
    var config: Config? = null
        private set

    init {
        load()
    }

    fun load() {
        val configFile = File(configFilePath)
        if (!configFile.parentFile.exists()) {
            configFile.parentFile.mkdirs()
        }
        if (!configFile.exists()) {
            try {
                configFile.createNewFile()
                config = Config()
                save()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            FileInputStream(configFile).use { inputStream ->
                config = yaml.load(inputStream)
            }
            // Check if local config version is not set, is lower or higher than the plugin's config version
            val pluginConfig = Config()
            if (config?.configVersion == null || config?.configVersion != pluginConfig.configVersion) {
                // Create backup directory and copy local config file
                val backupDir = File(configFile.parentFile, "config-backup")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }
                val backupFile = File(backupDir, configFile.name)
                configFile.copyTo(backupFile, true)

                // Save plugin's config to local config file
                config = pluginConfig
                save()
            }
        }
    }

    private fun save() {
        FileWriter(configFilePath).use { writer ->
            writer.write("# This is the internal version of the config, DO NOT MODIFY THIS VALUE\n")
            writer.write("configVersion: ${config?.configVersion}\n")

            writer.write("# Redis configuration\n")
            writer.write("redis:\n")
            writer.write("  host: ${config?.redis?.host}\n")
            writer.write("  port: ${config?.redis?.port}\n")
            writer.write("  username: ${config?.redis?.username}\n")
            writer.write("  password: ${config?.redis?.password}\n")
            writer.write("  useSsl: ${config?.redis?.useSsl}\n")
            writer.write("  channel: ${config?.redis?.channel}\n")

            writer.write("# CloudNet hook\n")
            writer.write("cloudnet:\n")
            writer.write("  enabled: ${config?.cloudnet?.enabled}\n")
            writer.write("  # Here you can enable or disable that the proxy id is the CloudNet service id\n")
            writer.write("  cloudnetUseServiceId: ${config?.cloudnet?.cloudnetUseServiceId}\n")

            writer.write("# Here you can enable or disable the player count sync\n")
            writer.write("playerCountSync: ${config?.playerCountSync}\n")

            writer.write("# Version control\n")
            writer.write("versionControl:\n")
            writer.write("  # Here you can enable or disable to check the players version\n")
            writer.write("  enabled: ${config?.versionControl?.enabled}\n")
            writer.write("  protocolVersion: ${config?.versionControl?.protocolVersion}\n")
            writer.write("  kickMessage: ${config?.versionControl?.kickMessage}\n")

            writer.write("# Messages\n")
            writer.write("messages:\n")
            writer.write("  # Here you can set the prefix for RediVelocity. For colorcodes you have to use minimessages\n")
            writer.write("  prefix: ${config?.messages?.prefix}\n")
            writer.write("  # Here you can set the message for the kick message if you are blocked from the proxy. For colorecodes you have to use minimessages\n")
            writer.write("  kickMessage: ${config?.messages?.kickMessage}\n")

            writer.write("# Here you can enable or disable the resourcepack sending over the proxy\n# Not yet implemented, will be implemented in the next update or smth\n")
            writer.write("resourcepack:\n")
            writer.write("  enabled: ${config?.resourcepack?.enabled}\n")
            writer.write("  # Here you can enable or disable the force of the resourcepack\n")
            writer.write("  forceResourcepack: ${config?.resourcepack?.forceResourcepack}\n")
            writer.write("  # Here you can put the URL of the resourcepack\n")
            writer.write("  resourcepackUrl: ${config?.resourcepack?.resourcepackUrl}\n")
            writer.write("  # Here you can put the message for the resourcepack. For colorcodes you have to use minimessages\n")
            writer.write("  resourcepackMessage: ${config?.resourcepack?.resourcepackMessage}\n")
            writer.write("  # Here you can put the message for the kick message if the player doesn't accept the resourcepack. For colorcodes you have to use minimessages\n")
            writer.write("  resourcepackKickMessage: ${config?.resourcepack?.resourcepackKickMessage}\n")
        }
    }
}