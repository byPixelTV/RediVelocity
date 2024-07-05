package de.bypixeltv.redivelocity.managers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.utils.Version
import net.kyori.adventure.text.minimessage.MiniMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.function.Consumer

class UpdateManager @Inject constructor(private val rediVelocity: RediVelocity, private val proxy: ProxyServer) {

    private val miniMessages = MiniMessage.miniMessage()

    fun checkForUpdate() {
        rediVelocity.sendLogs("Checking for updates...")
        getLatestReleaseVersion { version ->
            val pluginOptional = proxy.pluginManager.getPlugin("redivelocity")
            if (pluginOptional.isPresent) {
                val plugin = pluginOptional.get()
                val currentVersionString = plugin.description.version.orElse("0") // Default to "0" if not present
                try {
                    val currentVersion = Version.fromString(currentVersionString)
                    val latestVersion = Version.fromString(version)
                    // Compare versions and notify accordingly
                    if (latestVersion <= currentVersion) {
                        proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <green>The plugin is up to date!</green>"))
                    } else {
                        proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>The plugin is not up to date!</red>"))
                        proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Current version: <red>v$currentVersionString</red>"))
                        proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Available update: <green>v$version</green>"))
                        proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Download available at: <aqua>https://github.com/byPixelTV/RediVelocity/releases</aqua>"))
                    }
                } catch (e: NumberFormatException) {
                    rediVelocity.sendErrorLogs("Invalid version format: $currentVersionString")
                }
            } else {
                rediVelocity.sendErrorLogs("Plugin 'redivelocity' not found.")
            }
        }
    }

    private fun getLatestReleaseVersion(consumer: Consumer<String>) {
        try {
            val url = URI("https://api.github.com/repos/byPixelTV/RediVelocity/releases/latest")
            val reader = BufferedReader(InputStreamReader(url.toURL().openStream()))
            val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
            var tagName = jsonObject["tag_name"].asString
            tagName = tagName.removePrefix("v")
            consumer.accept(tagName)
        } catch (e: IOException) {
            rediVelocity.sendErrorLogs("Checking for updates failed!")
        }
    }
}