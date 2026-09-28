package dev.bypixel.redivelocity.redis

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScriptOutputType

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object RedisPlayerState {
    private val script = RedisPlayerState::class.java.getResource("/redis/player.lua")!!.readText()

    suspend fun update(operation: String, uuid: String, session: String, vararg values: String): Boolean {
        val plugin = RediVelocity.instance
        return plugin.lettuceClient.withCoroutines {
            it.eval<Long>(script, ScriptOutputType.INTEGER, emptyArray<String>(),
                operation, plugin.proxyId, plugin.instanceId, uuid, session, *values) == 1L
        }
    }
}
