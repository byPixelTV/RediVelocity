package dev.bypixel.redivelocity.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

object SerializationHelpers {
    private val mm = MiniMessage.miniMessage()

    /**
     * Converts a MiniMessage formatted string to a plaintext string.
     * Example: "<green>Hello <aqua>World" -> "Hello World"
     *
     * @param string The MiniMessage formatted string
     * @return The plaintext string
     */
    fun convertToPlaintext(string: String): String {
        return PlainTextComponentSerializer.plainText().serialize(mm.deserialize(convertToMinimessage(string)))
    }

    /**
     * Converts a Component to a plaintext string.
     * Example: Component representing "Hello World" -> "Hello World"
     *
     * @param component The Component to convert
     * @return The plaintext string
     */
    fun convertComponentToPlaintext(component: Component): String {
        return PlainTextComponentSerializer.plainText().serialize(component)
    }

    /**
     * Converts a legacy formatted string (using § or &) to a MiniMessage formatted string.
     * Example: "§aHello §bWorld" -> "<green>Hello <aqua>World"
     *
     * @param input The legacy formatted string
     * @return The MiniMessage formatted string
     */
    fun convertToMinimessage(input: String): String {
        val legacySerializer = LegacyComponentSerializer.builder().character('&').extractUrls().hexColors().build()

        // Deserialize the legacy formatted string to a Component
        val component = legacySerializer.deserialize(input.replace("§", "&"))

        // Serialize the Component to a MiniMessage formatted string
        val miniMessageString = MiniMessage.miniMessage().serialize(component)

        return miniMessageString.replace("\\", "")
    }

    fun convertToMinimessage(component: Component): String {
        val legacySerializer = LegacyComponentSerializer.builder().character('&').extractUrls().hexColors().build()

        // Serialize the Component to a legacy formatted string
        val legacyString = legacySerializer.serialize(component)

        // Convert the legacy formatted string to a MiniMessage formatted string
        return convertToMinimessage(legacyString)
    }

    fun convertToLegacy(component: Component): String {
        val legacySerializer = LegacyComponentSerializer.builder().character('&').extractUrls().hexColors().build()
        return legacySerializer.serialize(component)
    }

    fun convertToLegacyParagraphs(component: Component): String {
        val legacySerializer = LegacyComponentSerializer.builder().character('&').extractUrls().hexColors().build()
        return legacySerializer.serialize(component).replace("&", "§").replace("\n", "\n")
    }
}