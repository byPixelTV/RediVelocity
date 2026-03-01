package dev.bypixel.redivelocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import dev.bypixel.lettucewrapper.LettuceRedisClient
import dev.bypixel.lettucewrapper.RedisCredentials
import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.bypixel.redivelocity.cache.ProxyCache
import dev.bypixel.redivelocity.command.FindCommand
import dev.bypixel.redivelocity.command.RediVelocityCommand
import dev.bypixel.redivelocity.connection.RedisConnectionTask
import dev.bypixel.redivelocity.election.ElectionScheduler
import dev.bypixel.redivelocity.event.*
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountScheduler
import dev.bypixel.redivelocity.heartbeat.HeartbeatScheduler
import dev.bypixel.redivelocity.pubsub.ConnectListener
import dev.bypixel.redivelocity.pubsub.KickListener
import dev.bypixel.redivelocity.pubsub.LeaderElectionListener
import dev.bypixel.redivelocity.pubsub.PlayercountListener
import dev.bypixel.redivelocity.registration.ProxyRegistrationScheduler
import dev.bypixel.redivelocity.util.CloudUtil
import dev.bypixel.redivelocity.util.ProxyIdGenerator
import dev.bypixel.redivelocity.util.RediVelocityLogger
import dev.bypixel.redivelocity.util.UpdateUtil
import dev.dejvokep.boostedyaml.YamlDocument
import dev.dejvokep.boostedyaml.dvs.versioning.BasicVersioning
import dev.dejvokep.boostedyaml.route.Route
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIVelocityConfig
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.bxteam.quark.velocity.VelocityLibraryManager
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class RediVelocity @Inject constructor(val proxy: ProxyServer) {
    lateinit var libraryManager: VelocityLibraryManager<RediVelocity>

    lateinit var lettuceClient: LettuceRedisClient
    lateinit var config: YamlDocument
    lateinit var messageConfig: YamlDocument
    lateinit var proxyId: String

    private val logger = LoggerFactory.getLogger(RediVelocity::class.java)

    private var wasFirstProxy = false

    companion object {
        lateinit var instance: RediVelocity
            private set
        lateinit var server: ProxyServer
            private set
    }

    init {
        System.setProperty("io.lettuce.core.epoll", "false")
        System.setProperty("io.lettuce.core.iouring", "false")
        System.setProperty("io.lettuce.core.kqueue", "false")

        CommandAPI.onLoad(CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true).setNamespace("redivelocity"))
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        instance = this
        server = proxy

        CommandAPI.onEnable()

        setupLibraryManager()
        loadConfigs()
        initLettuceClientFromConfig()

        RedisListener.setLettuceClient(lettuceClient)

        if (!lettuceClient.connection.isOpen) {
            RediVelocityLogger.error("Failed to connect to Redis at ${config.getString(Route.fromString("redis.host"))}:${config.getInt(Route.fromString("redis.port"))}.")
            return
        } else {
            RediVelocityLogger.success("Connected to Redis at ${config.getString(Route.fromString("redis.host"))}:${config.getInt(Route.fromString("redis.port"))}")
        }

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            determineProxyId()
            delay(500)
            registerProxyInRedis()
            handleFirstProxyCleanupIfNeeded()
            sendAddProxyEvent()
            startBackgroundJobs()
        }

        RediVelocityLogger.success("RediVelocity v${proxy.pluginManager.getPlugin("redivelocity").get().description.version.orElse("unknown")} has been enabled!")

        proxy.scheduler.buildTask(this, Runnable {
            if (config.getBoolean(Route.fromString("update-check.enabled"))) {
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    UpdateUtil.updateJob.start()
                }
            }

            KickListener
            ConnectListener
            LeaderElectionListener
            PlayercountListener

            RediVelocityCommand().register()
            FindCommand().register()

            PlayerCache.register()
            ProxyCache.register()

            if (config.getBoolean(Route.fromString("playercount-sync.enabled"))) {
                proxy.eventManager.register(this, ProxyPingListener)
            }
            proxy.eventManager.register(this, PostLoginListener)
            proxy.eventManager.register(this, ServerSwitchListener)
            proxy.eventManager.register(this, DisconnectListener)
            proxy.eventManager.register(this, ServerRegisteredListener)
            proxy.eventManager.register(this, ServerUnregisteredListener)
            proxy.eventManager.register(this, PreLoginListener)

            val registeredServers =
                proxy.allServers.associate { it.serverInfo.name to it.serverInfo.address.toString() }
            if (registeredServers.isNotEmpty()) {
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    lettuceClient.withCoroutines {
                        it.hset("redivelocity:registered-servers:$proxyId", registeredServers)
                    }
                }
            }
        }).delay(500, TimeUnit.MILLISECONDS).schedule()
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            lettuceClient.sendMessage(
                JSONObject().apply {
                    put("action", "REMOVE")
                    put("id", proxyId)
                }, "redivelocity:proxy-events"
            )

            lettuceClient.withCoroutines {
                it.hdel("redivelocity:proxies", proxyId)
                it.hdel("redivelocity:heartbeats", proxyId)
                it.hdel("redivelocity:proxy:player-counts", proxyId)
                it.del("redivelocity:registered-servers:$proxyId")
                if (it.get("redivelocity:leader") == proxyId) {
                    it.del("redivelocity:leader")
                }
            }
            lettuceClient.deleteHashFieldByValueAsync("redivelocity:proxy:players", proxyId)

            if (lettuceClient.withCoroutines { it.hvals("redivelocity:proxies").toList().isEmpty() }) {
                RediVelocityLogger.info("Last proxy shutting down, clearing all RediVelocity data...")
                lettuceClient.withCoroutines {
                    it.del(
                        "redivelocity:proxy:players",
                        "redivelocity:proxy:player-counts",
                        "redivelocity:proxy:heartbeats",
                        "redivelocity:player:servers",
                        "redivelocity:proxies",
                        "redivelocity:global:playercount",
                        "redivelocity:player:names",
                        "redivelocity:leader",
                        "redivelocity:registered-servers:*"
                    )
                }
            }

            CommandAPI.onDisable()

            RedisListener.unregisterListener(KickListener)
            RedisListener.unregisterListener(ConnectListener)
            RedisListener.unregisterListener(LeaderElectionListener)
            RedisListener.unregisterListener(PlayercountListener)
            ElectionScheduler.job.cancelAndJoin()
            ProxyRegistrationScheduler.job.cancelAndJoin()
            HeartbeatScheduler.job.cancelAndJoin()
            RedisConnectionTask.job.cancelAndJoin()
            if (config.getBoolean(Route.fromString("update-check.enabled"))) {
                UpdateUtil.updateJob.cancelAndJoin()
            }
            PlayercountScheduler.proxyPlayerCountUpdateScheduler.cancelAndJoin()
            PlayercountScheduler.globalPlayerCountCalcScheduler.cancelAndJoin()
            PlayerCache.unregister()
            ProxyCache.unregister()

            lettuceClient.close()
        }
    }

    private fun setupLibraryManager() {
        libraryManager = VelocityLibraryManager(
            this,
            logger,
            Path("plugins/redivelocity"),
            proxy.pluginManager
        )
        libraryManager.loadFromGradle()
    }

    private fun loadConfigs() {
        val configInputStream = object {}.javaClass.getResourceAsStream("/config.yml")
        val messagesInputStream = object {}.javaClass.getResourceAsStream("/messages.yml")

        config = YamlDocument.create(File("plugins/redivelocity/config.yml"), configInputStream!!, GeneralSettings.builder().setKeyFormat(
            GeneralSettings.KeyFormat.OBJECT).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(
            BasicVersioning("config-version")
        ).build())
        messageConfig = YamlDocument.create(File("plugins/redivelocity/messages.yml"), messagesInputStream!!, GeneralSettings.builder().setKeyFormat(
            GeneralSettings.KeyFormat.OBJECT).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(
            BasicVersioning("config-version")
        ).build())
    }

    private fun initLettuceClientFromConfig() {
        val redisHost = config.getString(Route.fromString("redis.host"))
        val redisPort = config.getInt(Route.fromString("redis.port"))
        val redisPassword = config.getString(Route.fromString("redis.password"), null)
        val redisDatabase = config.getInt(Route.fromString("redis.database"), 0)
        val redisUser = config.getString(Route.fromString("redis.username"), null)
        val redisSsl = config.getBoolean(Route.fromString("redis.ssl"), false)
        val redisConnectionTimeout = config.getLong(Route.fromString("redis.connectionTimeout"), 2000L)
        val redisConnectionPoolSize = config.getInt(Route.fromString("redis.connectionPoolSize"), 10)
        val redisAllowSelfSignedCertificates = config.getBoolean(Route.fromString("redis.allowSelfSignedCertificates"), false)
        val redisTrustStorePath = config.getString(Route.fromString("redis.trustStorePath"), null)
        val redisTrustStorePassword = config.getString(Route.fromString("redis.trustStorePassword"), null)

        try {
            lettuceClient = if (!redisSsl) {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        timeoutMillis = redisConnectionTimeout,
                    ), RediVelocityCoroutineScope, redisConnectionPoolSize)
            } else if ((redisTrustStorePath == null || redisTrustStorePassword == null) && redisSsl) {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        true,
                        redisAllowSelfSignedCertificates,
                        timeoutMillis = redisConnectionTimeout
                    ), RediVelocityCoroutineScope, redisConnectionPoolSize)
            } else {
                LettuceRedisClient(
                    RedisCredentials(
                        redisHost,
                        redisPort,
                        redisUser,
                        redisPassword,
                        redisDatabase,
                        true,
                        redisAllowSelfSignedCertificates,
                        timeoutMillis = redisConnectionTimeout,
                        trustStorePath = redisTrustStorePath,
                        trustStorePassword = redisTrustStorePassword
                    ), RediVelocityCoroutineScope, redisConnectionPoolSize)
            }
        } catch (e: Exception) {
            RediVelocityLogger.error("Could not connect to the Redis server, please check your configuration.")
            e.printStackTrace()
        }
    }

    private suspend fun determineProxyId() {
        if (config.getBoolean(Route.fromString("cloud-support.enabled"))) {
            proxyId = CloudUtil.getServiceName(config.getString(Route.fromString("cloud-support.cloud-system")))
            RediVelocityLogger.success("Using cloud service name as proxy ID: $proxyId")
            return
        }

        if (config.getBoolean(Route.fromString("proxy-id.auto-generate")) == false) {
            val configId = config.getString(Route.fromString("proxy-id.id"))
            if (configId.isNullOrBlank()) {
                RediVelocityLogger.error("The configured proxy ID is blank! Please choose a unique ID. Will generate a random ID instead.")
                proxyId = ProxyIdGenerator.generate()
                return
            }

            if (ProxyIdGenerator.getExistingIds().contains(configId)) {
                RediVelocityLogger.error("The configured proxy ID '$configId' is already in use by another proxy! Please choose a unique ID. Will generate a random ID instead.")
                proxyId = ProxyIdGenerator.generate()
                RediVelocityLogger.success("Generated random proxy ID: $proxyId")
            } else {
                proxyId = configId
                RediVelocityLogger.success("Using configured proxy ID: $proxyId")
            }
        } else {
            proxyId = ProxyIdGenerator.generate()
            RediVelocityLogger.success("Generated random proxy ID: $proxyId")
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun registerProxyInRedis() {
        lettuceClient.withCoroutines {
            it.hset("redivelocity:proxies", proxyId, proxyId)
        }
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    private suspend fun handleFirstProxyCleanupIfNeeded() {
        val proxyIdsSize = ProxyIdGenerator.getExistingIds().size
        wasFirstProxy = proxyIdsSize == 1 || proxyIdsSize == 0

        if (wasFirstProxy) {
            RediVelocityLogger.info("This proxy is the first one to connect to Redis, clearing old data...")
            lettuceClient.withCoroutines {
                it.set("redivelocity:leader", proxyId)
            }
            lettuceClient.withCoroutines {
                it.del(
                    "redivelocity:proxy:players",
                    "redivelocity:proxy:player-counts",
                    "redivelocity:proxy:heartbeats",
                    "redivelocity:player:servers",
                    "redivelocity:proxies",
                    "redivelocity:global:playercount",
                    "redivelocity:player:names",
                    "redivelocity:leader",
                    "redivelocity:registered-servers:*"
                )
            }
            lettuceClient.withCoroutines {
                it.hset("redivelocity:proxies", proxyId, proxyId)
            }
        }
    }

    private fun sendAddProxyEvent() {
        lettuceClient.sendMessage(
            JSONObject().apply {
                put("action", "ADD")
                put("id", proxyId)
            }, "redivelocity:proxy-events")
    }

    private fun startBackgroundJobs() {
        HeartbeatScheduler.job.start()
        ElectionScheduler.job.start()
        RedisConnectionTask.job.start()
        ProxyRegistrationScheduler.job.start()
        PlayercountScheduler.proxyPlayerCountUpdateScheduler.start()
        PlayercountScheduler.globalPlayerCountCalcScheduler.start()
    }
}
