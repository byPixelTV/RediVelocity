package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity

object ConfigListener : RedisListener("redivelocity:config") {
    override fun onLettuceMessage(action: String, raw: String) {
        when (action) {
            "reload" -> {
                RediVelocity.instance.reloadConfigs()
            }
        }
    }
}