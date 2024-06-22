package de.bypixeltv.redivelocity.utils

import com.google.inject.Inject
import de.bypixeltv.redivelocity.config.Config
import de.bypixeltv.redivelocity.managers.RedisController

class ProxyIdGenerator @Inject constructor(private val redisController: RedisController, private val config: Config) {
    fun generateProxyId(): String {
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