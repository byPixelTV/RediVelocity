package dev.bypixel.redivelocity.model

import dev.bypixel.lettucewrapper.listener.LettuceMessage
import kotlinx.serialization.Serializable

@Serializable
data class SetMaxPlayersMessage(
    val maxPlayers: Int
) : LettuceMessage("set-max-players", "redivelocity:login-config")