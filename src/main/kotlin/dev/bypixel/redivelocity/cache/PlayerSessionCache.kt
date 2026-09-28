package dev.bypixel.redivelocity.cache

import com.velocitypowered.api.proxy.Player
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.Collections
import java.util.IdentityHashMap

object PlayerSessionCache {
    // Bind sessions to connections: a late disconnect must not remove a newer login's token.
    private val sessions = Collections.synchronizedMap(IdentityHashMap<Player, String>())
    private val locks = Array(256) { Mutex() }

    fun create(player: Player): String = UUID.randomUUID().toString().also { sessions[player] = it }
    fun get(player: Player): String? = sessions[player]
    fun remove(player: Player): String? = sessions.remove(player)

    suspend fun <T> withLock(uuid: UUID, action: suspend () -> T): T =
        locks[(uuid.hashCode() and Int.MAX_VALUE) % locks.size].withLock { action() }
}
