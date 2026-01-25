package dev.bypixel.redivelocity.event

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.server.ServerUnregisteredEvent
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object ServerUnregisteredListener {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    @Subscribe
    fun onServerConnect(event: ServerUnregisteredEvent) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("action", "SERVER_UNREGISTERED")
                put("serverName", event.unregisteredServer.serverInfo.name)
                put("proxyId", RediVelocity.instance.proxyId)
                put("address", event.unregisteredServer.serverInfo.address.toString())
                put("timestamp", System.currentTimeMillis())
            }

            RediVelocity.instance.lettuceClient.withCoroutines {
                it.hdel(
                    "redivelocity:registered-servers:${RediVelocity.instance.proxyId}",
                    event.unregisteredServer.serverInfo.name
                )
                RediVelocity.instance.lettuceClient.sendMessage(json, "redivelocity:server-events")
            }
        }
    }
}