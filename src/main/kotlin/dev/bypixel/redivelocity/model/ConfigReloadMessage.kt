package dev.bypixel.redivelocity.model

import dev.bypixel.lettucewrapper.listener.LettuceMessage
import kotlinx.serialization.Serializable

@Serializable
data class ConfigReloadMessage(
    val reload: Boolean
) : LettuceMessage("reload", "redivelocity:config")