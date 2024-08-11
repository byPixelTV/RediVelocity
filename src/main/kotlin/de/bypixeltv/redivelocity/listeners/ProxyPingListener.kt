package de.bypixeltv.redivelocity.listeners

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import de.bypixeltv.redivelocity.RediVelocity
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

@Singleton
class ProxyPingListener @Inject constructor(
    rediVelocity: RediVelocity
) {

    private val redisController = rediVelocity.getRedisController()

    @Subscribe
    @Suppress("UNUSED")
    fun onProxyPing(event: ProxyPingEvent) {
        val players = redisController.getString("rv-global-playercount")
        val ping = event.ping.asBuilder()
        ping.onlinePlayers(players?.toInt() ?: 0)
        event.ping = ping.build()
    }
}