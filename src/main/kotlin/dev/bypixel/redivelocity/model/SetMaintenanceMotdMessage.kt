package dev.bypixel.redivelocity.model

import dev.bypixel.lettucewrapper.listener.LettuceMessage
import kotlinx.serialization.Serializable

@Serializable
data class SetMaintenanceMotdMessage(
    val motd: String
) : LettuceMessage("set-maintenance-motd", "redivelocity:login-config")