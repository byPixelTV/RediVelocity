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
import dev.bypixel.redivelocity.election.ElectionScheduler
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
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.bxteam.quark.velocity.VelocityLibraryManager
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

class RediVelocity @Inject constructor(val proxy: ProxyServer) {
    lateinit var lettuceClient: LettuceRedisClient
    lateinit var config: YamlDocument
    lateinit var messageConfig: YamlDocument
    lateinit var proxyId: String

    lateinit var libraryManager: VelocityLibraryManager<RediVelocity>

    private var wasFirstProxy = false

    companion object {
        lateinit var instance: RediVelocity
            private set
        lateinit var server: ProxyServer
            private set
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onProxyInitialize(event: ProxyInitializeEvent) {
        instance = this
        server = proxy

        libraryManager = VelocityLibraryManager(
            this,
            LoggerFactory.getLogger(RediVelocity::class.java),
            Path("plugins/redivelocity"),
            proxy.pluginManager
        )
        libraryManager.loadFromGradle()

        val configInputStream = object {}.javaClass.getResourceAsStream("/config.yml")
        val messagesInputStream = object {}.javaClass.getResourceAsStream("/messages.yml")

        config = YamlDocument.create(File("config.yml"), configInputStream!!, GeneralSettings.builder().setKeyFormat(
            GeneralSettings.KeyFormat.OBJECT).build(), LoaderSettings.builder().setAutoUpdate(true).build(), DumperSettings.DEFAULT, UpdaterSettings.builder().setVersioning(
            BasicVersioning("config-version")
        ).build())
        messageConfig = YamlDocument.create(File("messages.yml"), messagesInputStream!!, UpdaterSettings.builder().setVersioning(
            BasicVersioning("config-version")
        ).build())

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            UpdateUtil.checkForUpdate()
        }

        val redisHost = config.getString(Route.fromString("redis.host"))
        val redisPort = config.getInt(Route.fromString("redis.port"))
        val redisPassword = config.getString(Route.fromString("redis.password"), null)
        val redisDatabase = config.getInt(Route.fromString("redis.database"), 0)
        val redisUser = config.getString(Route.fromString("redis.username"), null)

        lettuceClient = LettuceRedisClient(redisHost, redisPort, redisPassword, RediVelocityCoroutineScope, redisUser, redisDatabase)

        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            if (config.getBoolean(Route.from("cloud-support.enabled"))) {
                proxyId = CloudUtil.getServiceName(config.getString(Route.from("cloud-support.cloud-system")))
            } else if (!config.getBoolean(Route.from("proxy-id.auto-generate"))) {
                val configId = config.getString(Route.from("proxy-id.id"))

                if (configId.isNullOrBlank()) {
                    RediVelocityLogger.error("The configured proxy ID is blank! Please choose a unique ID. Will generate a random ID instead.")
                    proxyId = ProxyIdGenerator.generate()
                }

                if (ProxyIdGenerator.getExistingIds().contains(configId)) {
                    RediVelocityLogger.error("The configured proxy ID '$configId' is already in use by another proxy! Please choose a unique ID. Will generate a random ID instead.")
                    proxyId = ProxyIdGenerator.generate()
                }
                proxyId = configId
            } else {
                proxyId = ProxyIdGenerator.generate()
            }

            val proxyIdsSize = ProxyIdGenerator.getExistingIds().size

            wasFirstProxy = proxyIdsSize == 1 || proxyIdsSize == 0

            if (wasFirstProxy) {
                lettuceClient.commands.del("redivelocity:proxy:players")
                lettuceClient.commands.del("redivelocity:proxy:player-counts")
                lettuceClient.commands.del("redivelocity:proxy:heartbeats")
                lettuceClient.commands.del("redivelocity:player:servers")
                lettuceClient.commands.del("redivelocity:proxies")
                lettuceClient.commands.del("redivelocity:global:playercount")

            }
        }

        ElectionScheduler.job.start()

        RediVelocityLogger.success("RediVelocity v${proxy.pluginManager.getPlugin("redivelocity").get().description.version.orElse("unknown")} has been enabled!")

        proxy.scheduler.buildTask(this, Runnable {

        }).delay(2, TimeUnit.SECONDS).schedule()
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            lettuceClient.close()
            RediVelocityCoroutineScope.coroutineContext.cancelChildren()
            ElectionScheduler.job.cancelAndJoin()
        }
    }
}