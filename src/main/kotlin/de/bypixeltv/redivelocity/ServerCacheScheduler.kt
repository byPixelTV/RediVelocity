package de.bypixeltv.redivelocity

import de.bypixeltv.redivelocity.managers.RedisController
import java.util.concurrent.TimeUnit

class ServerCacheScheduler(private val plugin: RediVelocity, private val redisController: RedisController, private val proxyId: String) {
    init {
        val task = Runnable {
            val allServers = plugin.proxy.allServers
            allServers.forEach { server ->
                redisController.setHashField("rv-$proxyId-servers-servers", server.serverInfo.name, server.serverInfo.address.toString())
                redisController.setHashField("rv-$proxyId-servers-playercount", server.serverInfo.name, server.playersConnected.size.toString())
                redisController.setHashField("rv-$proxyId-servers-address", server.serverInfo.name, server.serverInfo.address.toString())
            }
        }

        // Schedule the task to run every 30 seconds
        plugin.proxy.scheduler.buildTask(plugin, task)
            .repeat(30, TimeUnit.SECONDS)
            .schedule()
    }
}