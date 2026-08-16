package dev.bypixel.redivelocity.model

data class MotdEntry(
    val id: String,
    val content: String,
    val playerInfo: String?,
    val protocolText: String?,
    val maintenance: Boolean
)