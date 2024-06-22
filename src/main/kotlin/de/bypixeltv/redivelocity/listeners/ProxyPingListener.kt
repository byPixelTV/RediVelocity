package de.bypixeltv.redivelocity.listeners

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.managers.RedisController

class ProxyPingListener @Inject constructor(private val server: ProxyServer, private val redisController: RedisController) {

    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val players = redisController.getString("rv-global-playercount")
        val ping = event.ping.asBuilder()
        ping.onlinePlayers(players?.toInt() ?: 0)
        event.ping = ping.build()
    }
}