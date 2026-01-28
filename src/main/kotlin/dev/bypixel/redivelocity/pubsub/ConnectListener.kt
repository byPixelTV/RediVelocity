package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import org.json.JSONObject
import java.util.*

object ConnectListener : RedisListener("redivelocity-connect") {
    override fun onMessage(message: String) {
        val jMsg = JSONObject(message)

        if (jMsg.has("uuid") && jMsg.has("server")) {
            val uuid = UUID.fromString(jMsg.getString("uuid"))
            val server = jMsg.getString("server")

            RediVelocity.instance.proxy.allPlayers.find { it.uniqueId == uuid }?.let { player ->
                player.createConnectionRequest(
                    RediVelocity.instance.proxy.getServer(server).orElseThrow {
                        IllegalArgumentException("Server $server not found")
                    }
                ).connectWithIndication()
            }
        }
    }
}