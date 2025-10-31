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

package dev.bypixel.redivelocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.proxy.ProxyServer
import dev.bypixel.lettucewrapper.LettuceRedisClient
import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.command.RediVelocityCommand
import dev.bypixel.redivelocity.election.ElectionScheduler
import dev.bypixel.redivelocity.event.DisconnectListener
import dev.bypixel.redivelocity.event.PostLoginListener
import dev.bypixel.redivelocity.event.ProxyPingListener
import dev.bypixel.redivelocity.event.ServerSwitchListener
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountScheduler
import dev.bypixel.redivelocity.heartbeat.HeartbeatScheduler
import dev.bypixel.redivelocity.cache.PlayerCache
import dev.bypixel.redivelocity.cache.ProxyCache
import dev.bypixel.redivelocity.command.FindCommand
import dev.bypixel.redivelocity.pubsub.KickListener
import dev.bypixel.redivelocity.pubsub.LeaderElectionListener
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
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
        CommandAPI.onLoad(CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true).setNamespace("redivelocity"))
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        instance = this
        server = proxy

        CommandAPI.onEnable()

        libraryManager = VelocityLibraryManager(
            this,
            logger,
            Path("plugins/redivelocity"),
            proxy.pluginManager
        )

        libraryManager.loadFromGradle()

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

        val redisHost = config.getString(Route.fromString("redis.host"))
        val redisPort = config.getInt(Route.fromString("redis.port"))
        val redisPassword = config.getString(Route.fromString("redis.password"), null)
        val redisDatabase = config.getInt(Route.fromString("redis.database"), 0)
        val redisUser = config.getString(Route.fromString("redis.username"), null)

        lettuceClient = LettuceRedisClient(redisHost, redisPort, redisPassword, RediVelocityCoroutineScope, redisUser, redisDatabase)

        RedisListener.setLettuceClient(lettuceClient)

        if (lettuceClient.connection.isOpen) {
            RediVelocityLogger.success("Connected to Redis at $redisHost:$redisPort")
        } else {
            RediVelocityLogger.error("Failed to connect to Redis at $redisHost:$redisPort.")
            return
        }

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            if (config.getBoolean(Route.fromString("cloud-support.enabled"))) {
                proxyId = CloudUtil.getServiceName(config.getString(Route.fromString("cloud-support.cloud-system")))
                RediVelocityLogger.success("Using cloud service name as proxy ID: $proxyId")
            } else if (config.getBoolean(Route.fromString("proxy-id.auto-generate")) == false) {
                val configId = config.getString(Route.fromString("proxy-id.id"))
                if (configId.isNullOrBlank()) {
                    RediVelocityLogger.error("The configured proxy ID is blank! Please choose a unique ID. Will generate a random ID instead.")
                    proxyId = ProxyIdGenerator.generate()
                }

                if (ProxyIdGenerator.getExistingIds().contains(configId)) {
                    RediVelocityLogger.error("The configured proxy ID '$configId' is already in use by another proxy! Please choose a unique ID. Will generate a random ID instead.")
                    proxyId = ProxyIdGenerator.generate()
                }
                proxyId = configId
                RediVelocityLogger.success("Using configured proxy ID: $proxyId")
            } else {
                proxyId = ProxyIdGenerator.generate()
                RediVelocityLogger.success("Generated random proxy ID: $proxyId")
            }

            delay(500)

            lettuceClient.commands.hset("redivelocity:proxies", proxyId, proxyId)

            val proxyIdsSize = ProxyIdGenerator.getExistingIds().size

            wasFirstProxy = proxyIdsSize == 1 || proxyIdsSize == 0

            if (wasFirstProxy) {
                RediVelocityLogger.info("This proxy is the first one to connect to Redis, clearing old data...")
                lettuceClient.commands.set("redivelocity:leader", proxyId)
                lettuceClient.commands.del(
                    "redivelocity:proxy:players",
                    "redivelocity:proxy:player-counts",
                    "redivelocity:proxy:heartbeats",
                    "redivelocity:player:servers",
                    "redivelocity:proxies",
                    "redivelocity:global:playercount",
                    "redivelocity:player:names",
                    "redivelocity:leader"
                )
                lettuceClient.commands.hset("redivelocity:proxies", proxyId, proxyId)
            }

            lettuceClient.sendMessage(
                JSONObject().apply {
                    put("action", "ADD")
                    put("id", proxyId)
                }, "redivelocity:proxy-events")
        }

        HeartbeatScheduler.job.start()

        ElectionScheduler.job.start()

        RediVelocityLogger.success("RediVelocity v${proxy.pluginManager.getPlugin("redivelocity").get().description.version.orElse("unknown")} has been enabled!")

        proxy.scheduler.buildTask(this, Runnable {
            if (config.getBoolean(Route.fromString("playercount-sync.enabled"))) {
                PlayercountScheduler.proxyPlayerCountUpdateScheduler.start()
                PlayercountScheduler.globalPlayerCountCalcScheduler.start()
            }

            if (config.getBoolean(Route.fromString("update-check.enabled"))) {
                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                    UpdateUtil.checkForUpdate()
                }
            }

            KickListener
            LeaderElectionListener

            RediVelocityCommand().register()
            FindCommand().register()

            PlayerCache.register()
            ProxyCache.register()

            proxy.eventManager.register(this, ProxyPingListener)
            proxy.eventManager.register(this, PostLoginListener)
            proxy.eventManager.register(this, ServerSwitchListener)
            proxy.eventManager.register(this, DisconnectListener)
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

            lettuceClient.commands.hdel("redivelocity:proxies", proxyId)
            lettuceClient.commands.hdel("redivelocity:heartbeats", proxyId)
            lettuceClient.deleteHashFieldByValueAsync("redivelocity:proxy:players", proxyId)
            lettuceClient.commands.hdel("redivelocity:proxy:player-counts", proxyId)
            if (lettuceClient.commands.get("redivelocity:leader") == proxyId) {
                lettuceClient.commands.del("redivelocity:leader")
            }

            CommandAPI.onDisable()

            RedisListener.unregisterListener(KickListener)
            RedisListener.unregisterListener(LeaderElectionListener)
            ElectionScheduler.job.cancelAndJoin()
            HeartbeatScheduler.job.cancelAndJoin()
            PlayerCache.unregister()
            ProxyCache.unregister()
            if (config.getBoolean(Route.fromString("playercount-sync.enabled"))) {
                PlayercountScheduler.proxyPlayerCountUpdateScheduler.cancelAndJoin()
                PlayercountScheduler.globalPlayerCountCalcScheduler.cancelAndJoin()
            }

            lettuceClient.close()
            RediVelocityCoroutineScope.coroutineContext.cancelChildren()
        }
    }
}