package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.model.SetMaintenanceMessage
import dev.bypixel.redivelocity.model.SetMaintenanceMotdMessage
import dev.bypixel.redivelocity.model.SetMaxPlayersMessage
import dev.bypixel.redivelocity.model.SetMotdMessage
import dev.dejvokep.boostedyaml.route.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object LoginConfigListener : RedisListener("redivelocity:login-config") {
    override fun onLettuceMessage(action: String, raw: String) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            when (action) {
                "set-maintenance" -> {
                    val message = Json.decodeFromString<SetMaintenanceMessage>(raw)

                    RediVelocity.instance.setMaintenance(message.state)
                    RediVelocity.instance.config.set(Route.fromString("login-configuration.maintenance.enabled"), message.state)
                    RediVelocity.instance.saveConfigs()
                }
                "set-maintenance-motd" -> {
                    val message = Json.decodeFromString<SetMaintenanceMotdMessage>(raw)

                    if (message.motd.isBlank() || message.motd == "random") {
                        RediVelocity.instance.setForceMaintenanceMotd("")
                        RediVelocity.instance.config.set(Route.fromString("login-configuration.maintenance.forced-motd"), "")
                    } else {
                        RediVelocity.instance.setForceMaintenanceMotd(message.motd)
                        RediVelocity.instance.config.set(Route.fromString("login-configuration.maintenance.forced-motd"), message.motd)
                    }
                    RediVelocity.instance.saveConfigs()
                }
                "set-motd" -> {
                    val message = Json.decodeFromString<SetMotdMessage>(raw)

                    if (message.motd.isBlank() || message.motd == "random") {
                        RediVelocity.instance.setForceMotd("")
                        RediVelocity.instance.config.set(Route.fromString("login-configuration.forced-motd"), "")
                    } else {
                        RediVelocity.instance.setForceMotd(message.motd)
                        RediVelocity.instance.config.set(Route.fromString("login-configuration.forced-motd"), message.motd)
                    }
                    RediVelocity.instance.saveConfigs()
                }
                "set-max-players" -> {
                    val maxPlayers = Json.decodeFromString<SetMaxPlayersMessage>(raw)

                    RediVelocity.instance.setMaxPlayers(maxPlayers.maxPlayers)
                    RediVelocity.instance.config.set(Route.fromString("login-configuration.max-players"), maxPlayers.maxPlayers)
                    RediVelocity.instance.saveConfigs()
                }
            }
        }
    }
}