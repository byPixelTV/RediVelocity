package de.bypixeltv.redivelocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.commands.RediVelocityCommand
import de.bypixeltv.redivelocity.config.ConfigLoader
import de.bypixeltv.redivelocity.listeners.DisconnectListener
import de.bypixeltv.redivelocity.listeners.PostLoginListener
import de.bypixeltv.redivelocity.listeners.ProxyPingListener
import de.bypixeltv.redivelocity.listeners.ServerSwitchListener
import de.bypixeltv.redivelocity.managers.RedisController
import de.bypixeltv.redivelocity.managers.UpdateManager
import de.bypixeltv.redivelocity.utils.ProxyIdGenerator
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIVelocityConfig
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bstats.velocity.Metrics

@Plugin(id = "redivelocity", name = "RediVelocity", version = "1.0.2", authors = ["byPixelTV"], description = "A Velocity plugin that sends Redis messages if a player joins the network, switches servers, or leaves the network.", url = "https://bypixeltv.de")
class RediVelocity @Inject constructor(val proxy: ProxyServer, private val metricsFactory: Metrics.Factory) {

    init {
        CommandAPI.onLoad(CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true))
    }

    private var redisController: RedisController? = null
    private var serverCacheScheduler: ServerCacheScheduler? = null
    private val configLoader: ConfigLoader = ConfigLoader("plugins/redivelocity/config.yml").apply { load() }
    private val miniMessages = MiniMessage.miniMessage()


    private var jsonFormat: String = "false"
    private var proxyId: String = ""

    fun getProxyId(): String {
        return proxyId
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
        metricsFactory.make(this, 22365)
        CommandAPI.onEnable()
        event.toString()

        // Load config and create RedisController
        configLoader.load()
        val config = configLoader.config
        redisController = config?.let { RedisController(this, it) }
        jsonFormat = config?.jsonFormat.toString()

        // Generate new proxy id
        do {
            proxyId = config?.let { ProxyIdGenerator(redisController!!, it).generateProxyId() }.toString()
            val rvProxiesList = redisController!!.getList("rv-proxies")
            var rvProxiesListFormatted = rvProxiesList?.joinToString()
            if (rvProxiesListFormatted?.isEmpty() == true) {
                rvProxiesListFormatted = "No proxies are connected"
            }
            this.sendLogs("Generated proxy ID: $proxyId")
            this.sendLogs("Connected proxies: ${rvProxiesListFormatted ?: "No proxies are connected"}")// Add logging
        } while (rvProxiesList?.contains(proxyId) == true)

        redisController!!.addToList("rv-proxies", arrayOf(proxyId))
        redisController!!.setHashField("rv-proxy-players", proxyId, 0.toString())
        if (redisController!!.getString("rv-global-playercount") == null) {
            redisController!!.setString("rv-global-playercount", 0.toString())
        }
        this.sendLogs("Creating new Proxy with ID: $proxyId")
        val version = proxy.pluginManager.getPlugin("redivelocity").get().description.version.toString()

        // Check for updates
        if (version.contains("-")) {
            this.sendLogs("This is a BETA build, things may not work as expected, please report any bugs on GitHub")
            this.sendLogs("https://github.com/byPixelTV/RediVelocity/issues")
        }

        UpdateManager(this, proxy).checkForUpdate()

        // Register listeners
        proxy.eventManager.register(this, ServerSwitchListener(this, redisController!!, config!!))
        proxy.eventManager.register(this, PostLoginListener(this, redisController!!, config, proxyId, proxy))
        proxy.eventManager.register(this, DisconnectListener(this, redisController!!, config, proxyId))
        proxy.eventManager.register(this, ProxyPingListener(proxy, redisController!!))

        // Register commands
        RediVelocityCommand(this, proxy, redisController!!, config)

        // Load ServerCacheScheduler
        serverCacheScheduler = ServerCacheScheduler(this, redisController!!, proxyId)
    }

    @Suppress("UNUSED")
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        event.toString()
        redisController!!.removeFromListByValue("rv-proxies", proxyId)
        redisController!!.deleteHashField("rv-proxy-players", proxyId)
        redisController!!.deleteHash("rv-players-cache")
        redisController!!.deleteHash("rv-$proxyId-servers-servers")
        redisController!!.deleteHash("rv-$proxyId-servers-players")
        redisController!!.deleteHash("rv-$proxyId-servers-playercount")
        redisController!!.deleteHash("rv-$proxyId-servers-address")
        // Check if any proxies are still connected if not, delete the hash
        if (redisController!!.getList("proxies")?.isEmpty() == true) {
            redisController!!.deleteHash("rv-proxy-players")
            redisController!!.deleteString("rv-global-playercount")
        }
        if (redisController != null) {
            redisController!!.shutdown()
        }
    }

}