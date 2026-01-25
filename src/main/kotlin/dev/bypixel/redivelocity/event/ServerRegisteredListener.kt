package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.server.ServerRegisteredEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object ServerRegisteredListener {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onServerConnect(event: ServerRegisteredEvent) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("action", "SERVER_REGISTERED")
                put("serverName", event.registeredServer.serverInfo.name)
                put("proxyId", RediVelocity.instance.proxyId)
                put("address", event.registeredServer.serverInfo.address.toString())
                put("timestamp", System.currentTimeMillis())
            }

            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hset("redivelocity:registered-servers:${RediVelocity.instance.proxyId}", event.registeredServer.serverInfo.name, event.registeredServer().serverInfo.address.toString())
                RediVelocity.instance.lettuceClient.sendMessage(json, "redivelocity:server-events")
            }
        }
    }
}