package de.bypixeltv.redivelocity.utils

import de.bypixeltv.redivelocity.RediVelocity
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton

@Singleton
class ProxyIdGenerator @Inject constructor(
    private val rediVelocityProvider: Provider<RediVelocity>
) {

    fun generate(): String {
        val redisController = rediVelocityProvider.get().getRedisController()

        redisController.getJedisPool().resource.use { jedis ->
            val proxiesList = jedis.lrange("rv-proxies", 0, -1)
            val ids = proxiesList.mapNotNull { it.removePrefix("Proxy-").toIntOrNull() }.sorted()
            var newId = 1
            for (id in ids) {
                if (id == newId) {
                    newId++
                } else {
                    break
                }
            }
            return "Proxy-$newId" // Return the counter as the new ID after the loop
        }
    }
}