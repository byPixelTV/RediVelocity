package de.bypixeltv.redivelocity.managers

import de.bypixeltv.redivelocity.RediVelocity
import com.velocitypowered.api.scheduler.ScheduledTask
import de.bypixeltv.redivelocity.config.Config
import org.json.JSONObject
import redis.clients.jedis.BinaryJedisPubSub
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RedisController(private val plugin: RediVelocity, private val config: Config) : BinaryJedisPubSub(), Runnable {

    private val jedisPool: JedisPool
    private var channelsInByte: Array<ByteArray>
    private val isConnectionBroken = AtomicBoolean(true)
    private val isConnecting = AtomicBoolean(false)
    private var connectionTask: ScheduledTask? = null

    init {
        val jConfig = JedisPoolConfig()
        val maxConnections = 10

        jConfig.maxTotal = maxConnections
        jConfig.maxIdle = maxConnections
        jConfig.minIdle = 1
        jConfig.blockWhenExhausted = true

        val password = config.redisPassword ?: ""
        jedisPool = if (password.isEmpty()) {
            JedisPool(
                jConfig,
                config.redisHost,
                config.redisPort,
                9000,
                config.useSsl
            )
        } else {
            JedisPool(
                jConfig,
                config.redisHost ?: "0.0.0.0",
                config.redisPort,
                9000,
                password,
                config.useSsl
            )
        }

        channelsInByte = setupChannels()
        connectionTask = plugin.proxy.scheduler.buildTask(plugin, this).repeat(5, TimeUnit.SECONDS).schedule()
    }

    override fun run() {
        if (!isConnectionBroken.get() || isConnecting.get()) {
            return
        }
        plugin.sendLogs("Connecting to Redis server...")
        isConnecting.set(true)
        try {
            jedisPool.resource.use { jedis ->
                isConnectionBroken.set(false)
                plugin.sendLogs("Connection to Redis server has established! Success!")
                jedis.subscribe(this, *channelsInByte)
            }
        } catch (e: Exception) {
            isConnecting.set(false)
            isConnectionBroken.set(true)
            plugin.sendErrorLogs("Connection to Redis server has failed! Please check your details in the configuration.")
            e.printStackTrace()
        }
    }

    fun shutdown() {
        connectionTask?.cancel()
        if (this.isSubscribed) {
            try {
                this.unsubscribe()
            } catch (e: Exception) {
                plugin.sendErrorLogs("Something went wrong during unsubscribing...")
                e.printStackTrace()
            }
        }
        jedisPool.close()
    }

    fun sendJsonMessage(event: String, proxyId: String, username: String, useruuid: String, clientbrand: String, userip: String, ping: Int, channel: String) {
        val jsonObject = JSONObject()
        jsonObject.put("action", event)
        jsonObject.put("proxyid", proxyId)
        jsonObject.put("username", username)
        jsonObject.put("uuid", useruuid)
        jsonObject.put("clientbrand", clientbrand)
        jsonObject.put("ipadress", userip)
        jsonObject.put("ping", ping)
        jsonObject.put("timestamp", System.currentTimeMillis())

        val jsonString = jsonObject.toString()

        // Publish the JSON string to the specified channel
        jedisPool.resource.use { jedis ->
            jedis.publish(channel, jsonString)
        }
    }

    fun sendMessage(message: String, channel: String) {
        jedisPool.resource.use { jedis ->
            jedis.publish(channel, message)
        }
    }

    fun removeFromListByValue(listName: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.lrem(listName, 0, value)
        }
    }

    fun setHashField(hashName: String, fieldName: String, value: String) {
        jedisPool.resource.use { jedis ->
            val type = jedis.type(hashName)
            if (type != "hash") {
                if (type == "none") {
                    jedis.hset(hashName, fieldName, value)
                } else {
                    System.err.println("Error: Key $hashName doesn't hold a hash. It holds a $type.")
                }
            } else {
                jedis.hset(hashName, fieldName, value)
            }
        }
    }

    fun deleteHashField(hashName: String, fieldName: String) {
        jedisPool.resource.use { jedis ->
            jedis.hdel(hashName, fieldName)
        }
    }

    fun deleteHash(hashName: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(hashName)
        }
    }

    fun addToList(listName: String, values: Array<String>) {
        jedisPool.resource.use { jedis ->
            values.forEach { value ->
                jedis.rpush(listName, value)
            }
        }
    }

    fun setListValue(listName: String, index: Int, value: String) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                System.err.println("Error: Index $index does not exist in the list $listName.")
            } else {
                jedis.lset(listName, index.toLong(), value)
            }
        }
    }

    fun getHashValuesAsPair(hashName: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        jedisPool.resource.use { jedis ->
            val keys = jedis.hkeys(hashName)
            for (key in keys) {
                values[key] = jedis.hget(hashName, key)
            }
        }
        return values
    }

    fun removeFromList(listName: String, index: Int) {
        jedisPool.resource.use { jedis ->
            val listLength = jedis.llen(listName)
            if (index >= listLength) {
                System.err.println("Error: Index $index does not exist in the list $listName.")
            } else {
                val tempKey = UUID.randomUUID().toString()
                jedis.lset(listName, index.toLong(), tempKey)
                jedis.lrem(listName, 0, tempKey)
            }
        }
    }

    fun deleteList(listName: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(listName)
        }
    }

    fun setString(key: String, value: String) {
        jedisPool.resource.use { jedis ->
            jedis.set(key, value)
        }
    }

    fun getString(key: String): String? {
        return jedisPool.resource.use { jedis ->
            jedis.get(key)
        }
    }

    fun deleteString(key: String) {
        jedisPool.resource.use { jedis ->
            jedis.del(key)
        }
    }

    fun getHashField(hashName: String, fieldName: String): String? {
        return jedisPool.resource.use { jedis ->
            jedis.hget(hashName, fieldName)
        }
    }

    fun getAllHashFields(hashName: String): Set<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.hkeys(hashName)
        }
    }

    fun getAllHashValues(hashName: String): List<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.hvals(hashName)
        }
    }

    fun getList(listName: String): List<String>? {
        return jedisPool.resource.use { jedis ->
            jedis.lrange(listName, 0, -1)
        }
    }

    fun getHashFieldNamesByValue(hashName: String, value: String): List<String> {
        val fieldNames = mutableListOf<String>()
        jedisPool.resource.use { jedis ->
            val keys = jedis.keys(hashName)
            for (key in keys) {
                val fieldsAndValues = jedis.hgetAll(key)
                for (entry in fieldsAndValues.entries) {
                    if (entry.value == value) {
                        fieldNames.add(entry.key)
                    }
                }
            }
        }
        return fieldNames
    }


    private fun setupChannels(): Array<ByteArray> {
        val channels = listOf("global", "messaging", "friends", "utils", "other") // replace with your actual channels
        return Array(channels.size) { channels[it].toByteArray(StandardCharsets.UTF_8) }
    }

    fun isRedisConnectionOffline(): Boolean {
        return isConnectionBroken.get()
    }

    fun getJedisPool(): JedisPool {
        return jedisPool
    } //

}