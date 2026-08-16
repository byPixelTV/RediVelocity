package dev.bypixel.redivelocity.model

import dev.bypixel.lettucewrapper.listener.LettuceMessage
import kotlinx.serialization.Serializable

@Serializable
data class SetMotdMessage(
    val motd: String
) : LettuceMessage("set-motd", "redivelocity:login-config")