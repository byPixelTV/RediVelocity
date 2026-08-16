package dev.bypixel.redivelocity.model

import dev.bypixel.lettucewrapper.listener.LettuceMessage
import kotlinx.serialization.Serializable

@Serializable
data class SetMaintenanceMessage(
    val state: Boolean
) : LettuceMessage("set-maintenance", "redivelocity:login-config")