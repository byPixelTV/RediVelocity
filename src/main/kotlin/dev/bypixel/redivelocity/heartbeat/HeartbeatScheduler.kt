package dev.bypixel.redivelocity.heartbeat

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.redis.RedisLifecycle
import dev.bypixel.redivelocity.util.RediVelocityLogger
import kotlinx.coroutines.*

object HeartbeatScheduler {
    val job = RediVelocityCoroutineScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        while (isActive) {
            try {
                if (!RedisLifecycle.update("heartbeat")) {
                    RediVelocityLogger.error("Proxy registration ownership was lost; refusing to overwrite another instance.")
                }
                if (RedisLifecycle.elect() == RediVelocity.instance.proxyId) {
                    RedisLifecycle.cleanup()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RediVelocityLogger.warn("Heartbeat/cleanup cycle failed: ${e.message}")
            }
            delay(10_000L)
        }
    }
}
