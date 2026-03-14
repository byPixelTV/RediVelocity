package dev.bypixel.redivelocity.antivpn

import com.github.benmanes.caffeine.cache.Caffeine
import dev.bypixel.redivelocity.RediVelocity
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import org.json.JSONObject
import java.time.Duration

object IpManager {

    private val caffeineCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofHours(6))
        .build<String, JSONObject>()

    private fun uuidKey(uuid: String) = "redivelocity:ipcache:uuid:$uuid"
    private fun ipKey(ip: String) = "redivelocity:ipcache:ip:$ip"

    /**
     * Validates whether a given string is a valid IPv4 or IPv6 address.
     *
     * @param ip The IP address string to validate
     * @return true if the string is a valid IPv4 or IPv6 address, false otherwise
     */
    fun isValidIpV4OrV6(ip: String): Boolean {
        val ipv4Pattern = Regex(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        val ipv6Pattern = Regex(
            "^(?:[a-fA-F0-9]{1,4}:){7}[a-fA-F0-9]{1,4}$"
        )
        return ipv4Pattern.matches(ip) || ipv6Pattern.matches(ip)
    }

    /**
     * Loads data from Redis into Caffeine if present
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun loadFromRedisToCaffeine(key: String): JSONObject? {
        val redis = RediVelocity.instance.lettuceClient.commands
        val dataString = redis.get(key) ?: return null

        val json = JSONObject(dataString)
        caffeineCache.put(key, json)
        return json
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun preloadAllIpCachesToCaffeine() {
        val redis = RediVelocity.instance.lettuceClient.commands

        var cursor = ScanCursor.of("0")
        val args = ScanArgs()
            .match("redivelocity:ipcache:*")
            .limit(200)

        while (true) {
            val scanResult = redis.scan(cursor, args) ?: break

            for (key in scanResult.keys) {
                // Skip if already cached
                if (IpManager.caffeineCache.getIfPresent(key) != null) continue

                val value = redis.get(key) ?: continue

                try {
                    val json = JSONObject(value)
                    IpManager.caffeineCache.put(key, json)
                } catch (_: Exception) {
                    // corrupted / invalid json -> skip
                }
            }

            if (scanResult.cursor == "0") break
            cursor = ScanCursor.of(scanResult.cursor)
        }
    }

    /**
     * Returns cached IP data or fetches it once if missing
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun cachePlayerIp(uuid: String, ip: String): JSONObject {
        val uuidKey = uuidKey(uuid)
        val ipKey = ipKey(ip)

        caffeineCache.getIfPresent(uuidKey)?.let { return it }
        caffeineCache.getIfPresent(ipKey)?.let { return it }

        loadFromRedisToCaffeine(uuidKey)?.let { return it }
        loadFromRedisToCaffeine(ipKey)?.let { return it }

        val data = IpQueryUtil.getIpData(ip)

        val redis = RediVelocity.instance.lettuceClient.commands
        val jsonString = data.toString()

        // Redis TTL 6h
        redis.setex(uuidKey, 1 * 60 * 60, jsonString)
        redis.setex(ipKey, 6 * 60 * 60, jsonString)

        // Caffeine
        caffeineCache.put(uuidKey, data)
        caffeineCache.put(ipKey, data)

        return data
    }

    /**
     * Read by UUID (no API call)
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getCachedIpData(uuid: String): JSONObject? {
        val key = uuidKey(uuid)

        caffeineCache.getIfPresent(key)?.let { return it }
        return loadFromRedisToCaffeine(key)
    }

    /**
     * Read by IP (no API call)
     */
    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    suspend fun getCachedIpDataByIp(ip: String): JSONObject? {
        val key = ipKey(ip)

        caffeineCache.getIfPresent(key)?.let { return it }
        return loadFromRedisToCaffeine(key)
    }
}