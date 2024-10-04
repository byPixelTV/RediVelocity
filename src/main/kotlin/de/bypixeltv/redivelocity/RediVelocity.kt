package de.bypixeltv.redivelocity

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.commands.RediVelocityCommand
import de.bypixeltv.redivelocity.config.ConfigLoader
import de.bypixeltv.redivelocity.listeners.*
import de.bypixeltv.redivelocity.managers.RedisController
import de.bypixeltv.redivelocity.managers.UpdateManager
import de.bypixeltv.redivelocity.utils.ProxyIdGenerator
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIVelocityConfig
import eu.cloudnetservice.ext.platforminject.api.PlatformEntrypoint
import eu.cloudnetservice.ext.platforminject.api.stereotype.Dependency
import eu.cloudnetservice.ext.platforminject.api.stereotype.PlatformPlugin
import eu.cloudnetservice.wrapper.holder.ServiceInfoHolder
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import net.kyori.adventure.text.minimessage.MiniMessage

@Singleton
@PlatformPlugin(
    platform = "velocity",
    name = "RediVelocity",
    version = "1.0.3",
    authors = ["byPixelTV"],
    description = "A fast, modern and clean alternative to RedisBungee on Velocity.",
    dependencies = [Dependency(name = "CloudNet-Bridge")]
)
class RediVelocity @Inject constructor(
    private val proxy: ProxyServer,
    private val proxyIdGenerator: ProxyIdGenerator,
    private val updateManager: UpdateManager,
    private val rediVelocityCommandProvider: Provider<RediVelocityCommand>,
    private val serviceInfoHolder: ServiceInfoHolder
) : PlatformEntrypoint {

    private val miniMessages: MiniMessage = MiniMessage.miniMessage()

    init {
        CommandAPI.onLoad(CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true))
        sendLogs("RediVelocity plugin loaded")
        proxy.eventManager.register(this, this) // Register the event listener
    }

    private val configLoader: ConfigLoader = ConfigLoader("plugins/redivelocity/config.yml").apply { load() }
    private val config = configLoader.config
    private var jsonFormat: String = config?.jsonFormat.toString()
    private var proxyId: String = ""
    private lateinit var redisController: RedisController

    fun getProxyId(): String {
        return proxyId
    }

    fun getRedisController(): RedisController {
        return redisController
    }

    fun sendLogs(message: String) {
        this.proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <yellow>$message</yellow>"))
    }

    fun sendErrorLogs(message: String) {
        this.proxy.consoleCommandSource.sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>$message</red>"))
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        sendLogs("Proxy initialization started")
        // Load config and create RedisController
        configLoader.load()
        val config = configLoader.config
        jsonFormat = config?.jsonFormat.toString()

        redisController = RedisController(this, config!!)
        CommandAPI.onEnable()
        event.toString()

        // Generate new proxy id
        proxyId = if (config.cloudnet.enabled) {
            if (config.cloudnet.cloudnetUseServiceId) {
                serviceInfoHolder.serviceInfo().name()
            } else {
                proxyIdGenerator.generate()
            }
        } else {
            proxyIdGenerator.generate()
        }

        redisController.addToList("rv-proxies", arrayOf(proxyId))
        redisController.setHashField("rv-proxy-players", proxyId, 0.toString())
        if (redisController.getString("rv-global-playercount") == null) {
            redisController.setString("rv-global-playercount", 0.toString())
        }
        sendLogs("Creating new Proxy with ID: $proxyId")
        val version = proxy.pluginManager.getPlugin("redivelocity").get().description.version.toString()

        // Check for updates
        if (version.contains("-")) {
            sendLogs("This is a BETA build, things may not work as expected, please report any bugs on GitHub")
            sendLogs("https://github.com/byPixelTV/RediVelocity/issues")
        }

        updateManager.checkForUpdate()

        // Register listeners
        proxy.eventManager.register(this, ServerSwitchListener(this, config))
        proxy.eventManager.register(this, PostLoginListener(this, config))
        proxy.eventManager.register(this, DisconnectListener(this, config))
        proxy.eventManager.register(this, ResourcePackListeners(proxy, config))

        if (config.playerCountSync) {
            proxy.eventManager.register(this, ProxyPingListener(this))
        }

        // Register commands
        rediVelocityCommandProvider.get().register()
        sendLogs("Proxy initialization completed")
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        sendLogs("Proxy shutdown started")
        event.toString()
        redisController.removeFromListByValue("rv-proxies", proxyId)
        redisController.deleteHashField("rv-proxy-players", proxyId)
        redisController.deleteHash("rv-players-name")
        redisController.deleteHash("rv-$proxyId-servers-servers")
        redisController.deleteHash("rv-$proxyId-servers-players")
        redisController.deleteHash("rv-$proxyId-servers-playercount")
        redisController.deleteHash("rv-$proxyId-servers-address")
        // Check if any proxies are still connected if not, delete the hash
        if (redisController.getList("rv-proxies")?.isEmpty() == true) {
            redisController.deleteHash("rv-proxy-players")
            redisController.deleteString("rv-global-playercount")
        }
        redisController.shutdown()
        sendLogs("Proxy shutdown completed")
    }
}