package dev.bypixel.redivelocity.registration

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.util.RediVelocityLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList

object ProxyRegistrationScheduler {
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    val job = CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            if (!RediVelocity.instance.lettuceClient.commands.hvals("redivelocity:proxies").toList().contains(
                    RediVelocity.instance.proxyId)) {
                RediVelocity.instance.lettuceClient.commands.hset("redivelocity:proxies", RediVelocity.instance.proxyId, RediVelocity.instance.proxyId)
                RediVelocityLogger.error("Proxy ID ${RediVelocity.instance.proxyId} was not registered. Registering now.")
            }

            delay(5000L)
        }
    }
}