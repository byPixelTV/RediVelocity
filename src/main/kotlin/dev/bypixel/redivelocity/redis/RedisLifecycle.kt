package dev.bypixel.redivelocity.redis

import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import kotlinx.coroutines.flow.toList

/** Atomic lifecycle operations for the shared, standalone Redis database. */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
object RedisLifecycle {
    private val script = RedisLifecycle::class.java.getResource("/redis/lifecycle.lua")!!.readText()

    suspend fun update(operation: String, id: String = RediVelocity.instance.proxyId, vararg values: String): Boolean =
        RediVelocity.instance.lettuceClient.withCoroutines {
            it.eval<Long>(script, ScriptOutputType.INTEGER, emptyArray<String>(),
                operation, id, RediVelocity.instance.instanceId, *values) == 1L
        }

    suspend fun elect(): String? = RediVelocity.instance.lettuceClient.withCoroutines {
        it.eval<String>(script, ScriptOutputType.VALUE, emptyArray<String>(), "elect", "", "")
            ?.takeIf(String::isNotEmpty)
    }

    suspend fun cleanup() {
        val ids = RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val candidates = mutableSetOf<String>()
            for (key in listOf("proxies", "heartbeats", "proxy:instances", "proxy:player-counts", "votes")) {
                candidates.addAll(redis.hkeys("redivelocity:$key").toList())
            }
            candidates.addAll(redis.hvals("redivelocity:player:proxies").toList())
            candidates.addAll(redis.smembers("redivelocity:existing-proxy-ids").toList())
            // A crash can leave only a server hash, with no registry/heartbeat entry.
            var cursor: ScanCursor = ScanCursor.INITIAL
            do {
                val page = checkNotNull(redis.scan(cursor, ScanArgs.Builder.matches("redivelocity:registered-servers:*").limit(100)))
                candidates.addAll(page.keys.map { it.removePrefix("redivelocity:registered-servers:") })
                cursor = page
            } while (!cursor.isFinished)
            candidates
        }
        for (id in ids) update("cleanup", id)
        val playerIds = RediVelocity.instance.lettuceClient.withCoroutines { redis ->
            val candidates = mutableSetOf<String>()
            for (field in listOf("servers", "names", "sessions", "ips")) {
                candidates.addAll(redis.hkeys("redivelocity:player:$field").toList())
            }
            candidates.removeAll(redis.hkeys("redivelocity:player:proxies").toList().toSet())
            candidates
        }
        for (uuid in playerIds) update("orphan-player", uuid)
    }
}
