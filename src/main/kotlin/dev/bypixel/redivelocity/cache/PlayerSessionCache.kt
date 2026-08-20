package dev.bypixel.redivelocity.cache

import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerSessionCache {

    private val sessions = ConcurrentHashMap<UUID, String>()

    fun create(uuid: UUID): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[uuid] = sessionId
        return sessionId
    }

    fun get(uuid: UUID): String? {
        return sessions[uuid]
    }

    fun remove(uuid: UUID): String? {
        return sessions.remove(uuid)
    }
}