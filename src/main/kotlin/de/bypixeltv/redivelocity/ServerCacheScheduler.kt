package de.bypixeltv.redivelocity

import de.bypixeltv.redivelocity.managers.RedisController
import java.util.concurrent.TimeUnit

class ServerCacheScheduler(private val plugin: RediVelocity, private val redisController: RedisController, private val proxyId: String) {
    init {
        val task = Runnable {
            plugin.sendLogs("Updating server cache...")
            val allServers = plugin.proxy.allServers
            println("All servers: $allServers")
            allServers.forEach { server ->
                println("Server: ${server.serverInfo.name} - Players: ${server.playersConnected}")
                redisController.setHashField("rv-$proxyId-servers-servers", server.serverInfo.name, server.serverInfo.address.toString())
                redisController.setHashField("rv-$proxyId-servers-playercount", server.serverInfo.name, server.playersConnected.size.toString())
                redisController.setHashField("rv-$proxyId-servers-address", server.serverInfo.name, server.serverInfo.address.toString())
            }
            plugin.sendLogs("Updated server cache")
        }

        // Schedule the task to run every 30 seconds
        plugin.proxy.scheduler.buildTask(plugin, task)
            .repeat(30, TimeUnit.SECONDS)
            .schedule()
    }
}