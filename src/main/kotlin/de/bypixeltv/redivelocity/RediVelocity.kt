package de.bypixeltv.redivelocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.managers.RedisController
import net.kyori.adventure.text.minimessage.MiniMessage
import org.slf4j.Logger

@Plugin(id = "redivelocity", name = "RediVelocity", version = "1.0.0", authors = ["byPixelTV"], description = "A Velocity plugin that sends Redis messages if a player joins the network, switches servers, or leaves the network.", url = "https://bypixeltv.de")
class RediVelocity @Inject constructor(val proxy: ProxyServer, private val logger: Logger, private val config: Config) {

    private var redisController: RedisController? = null
    private val configLoader: ConfigLoader = ConfigLoader("plugins/redivelocity/config.yml").apply { load() }
    private val miniMessages = MiniMessage.miniMessage()

    @Suppress("UNUSED")
    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        event.toString()
        configLoader.load()
        val config = configLoader.config
        redisController = config?.let { RedisController(this, it) }
        logger.info("RediVelocity has started!")

        val redisController: RedisController? = null
    }

    fun sendLogs(message: String) {
        this.proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <yellow>$message</yellow>"))
    }

    fun sendErrorLogs(message: String) {
        this.proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>$message</red>"))
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        if (redisController != null) {
            redisController!!.shutdown()
        }
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onPostLogin(event: PostLoginEvent) {
        val player = event.player
        if (config.jsonFormat) {
            redisController?.sendJsonMessage(
                "postLogin",
                player.username,
                player.uniqueId.toString(),
                player.clientBrand.toString(),
                player.remoteAddress.toString().split(":")[0].substring(1),
                config.redisChannel ?: "redivelocity-data"
            )
        } else {
            val msg = config.messageFormat
                ?.replace("{username}", player.username)
                ?.replace("{uuid}", player.uniqueId.toString())
                ?.replace("{clientbrand}", player.clientBrand.toString())
                ?.replace("{ip}", player.remoteAddress.toString().split(":")[0].substring(1))
                ?.replace("{timestamp}", System.currentTimeMillis().toString())
            redisController?.sendMessage(
                "postLogin;$msg",
                config.redisChannel ?: "redivelocity-data"
            )
        }
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onDisconnectEvent(event: DisconnectEvent) {
        val player = event.player
        if (config.jsonFormat) {
            redisController?.sendJsonMessage(
                "disconnect",
                player.username,
                player.uniqueId.toString(),
                player.clientBrand.toString(),
                player.remoteAddress.toString().split(":")[0].substring(1),
                config.redisChannel ?: "redivelocity-data"
            )
        } else {
            val msg = config.messageFormat
                ?.replace("{username}", player.username)
                ?.replace("{uuid}", player.uniqueId.toString())
                ?.replace("{clientbrand}", player.clientBrand.toString())
                ?.replace("{ip}", player.remoteAddress.toString().split(":")[0].substring(1))
                ?.replace("{timestamp}", System.currentTimeMillis().toString())
            redisController?.sendMessage(
                "disconnect;$msg",
                config.redisChannel ?: "redivelocity-data"
            )
        }
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onServerSwitch(event: ServerConnectedEvent) {
        val player = event.player
        if (config.jsonFormat) {
            redisController?.sendJsonMessage(
                "serverSwitch",
                player.username,
                player.uniqueId.toString(),
                player.clientBrand.toString(),
                player.remoteAddress.toString().split(":")[0].substring(1),
                config.redisChannel ?: "redivelocity-data"
            )
        } else {
            val msg = config.messageFormat
                ?.replace("{username}", player.username)
                ?.replace("{uuid}", player.uniqueId.toString())
                ?.replace("{clientbrand}", player.clientBrand.toString())
                ?.replace("{ip}", player.remoteAddress.toString().split(":")[0].substring(1))
                ?.replace("{timestamp}", System.currentTimeMillis().toString())
            redisController?.sendMessage(
                "serverSwitch;$msg",
                config.redisChannel ?: "redivelocity-data"
            )
        }
    }

}