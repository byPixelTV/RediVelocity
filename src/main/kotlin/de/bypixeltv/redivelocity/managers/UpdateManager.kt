package de.bypixeltv.redivelocity.managers

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.RediVelocity
import net.kyori.adventure.text.minimessage.MiniMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

class UpdateManager @Inject constructor(private val rediVelocity: RediVelocity, private val proxy: ProxyServer) {

    private val miniMessages = MiniMessage.miniMessage()
    private var updatever: String = ""

    fun checkForUpdate(pluginVersion: String) {
        rediVelocity.sendLogs("Checking for updates...")
        getLatestReleaseVersion { version ->
            val plugVer = proxy.pluginManager.getPlugin("redivelocity").get().description.version
            if (version <= plugVer.toString()) {
                proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <green>The plugin is up to date!</green>"))
            } else {
                proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>The plugin is not up to date!</red>"))
                proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Current version: <red>v${pluginVersion}</red>"))
                proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Available update: <green>v${version}</green>"))
                proxy.consoleCommandSource.sendMessage(miniMessages.deserialize(" - Download available at: <aqua>https://github.com/byPixelTV/RediVelocity/releases</aqua>"))
                updatever = version
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

    fun getUpdateVersion(currentVersion: String): CompletableFuture<String> {
        val future = CompletableFuture<String>()
        if (updatever.isNotEmpty()) {
            future.complete(updatever)
        } else {
            getLatestReleaseVersion(Consumer { version ->
                if (version <= currentVersion) {
                    future.cancel(true)
                } else {
                    updatever = version
                    future.complete(updatever)
                }
            })
        }
        return future
    }

}