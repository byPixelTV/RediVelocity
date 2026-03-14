package dev.bypixel.redivelocity.util

import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.WebhookEmbed
import club.minnced.discord.webhook.send.WebhookEmbedBuilder
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import com.google.common.util.concurrent.ThreadFactoryBuilder
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneId

object DiscordWebhookUtil {
    fun convertHexToDecimal(hexCode: String): Int {
        var hex = hexCode
        if (hex.startsWith("#")) {
            hex = hex.substring(1)
        }

        if (hex.length == 3) {
            val extendedHex = StringBuilder(6)
            for (c in hex) {
                extendedHex.append(c).append(c)
            }
            hex = extendedHex.toString()
        }

        return hex.toInt(16)
    }

    @JvmStatic
    fun sendMessage(url: String, message: String, threadId: Long? = null) {
        val client = if (threadId != null) {
            val threadFactory = ThreadFactoryBuilder()
                .setNameFormat("Webhook Thread")
                .setPriority(5)
                .setDaemon(true)
                .setUncaughtExceptionHandler { _, e -> e.printStackTrace() }
                .build()

            WebhookClientBuilder(url)
                .setThreadId(threadId)
                .setThreadFactory(threadFactory)
                .build()
        } else {
            WebhookClientBuilder(url).build()
        }

        client.send(message)
    }

    class EmbedBuilder {
        private val builder = WebhookEmbedBuilder()

        fun setTitle(title: String, url: String? = null): EmbedBuilder {
            builder.setTitle(WebhookEmbed.EmbedTitle(title, url ?: ""))
            return this
        }

        fun setDescription(description: String): EmbedBuilder {
            builder.setDescription(description)
            return this
        }

        fun setColor(color: String): EmbedBuilder {
            builder.setColor(convertHexToDecimal(color))
            return this
        }

        fun setThumbnailUrl(url: String): EmbedBuilder {
            builder.setThumbnailUrl(url)
            return this
        }

        fun setImageUrl(url: String): EmbedBuilder {
            builder.setImageUrl(url)
            return this
        }

        fun setFooter(text: String, iconUrl: String? = null): EmbedBuilder {
            builder.setFooter(WebhookEmbed.EmbedFooter(text, iconUrl))
            return this
        }

        fun setAuthor(name: String, iconUrl: String? = null, url: String? = null): EmbedBuilder {
            builder.setAuthor(WebhookEmbed.EmbedAuthor(name, iconUrl, url))
            return this
        }

        fun setTimestamp(): EmbedBuilder {
            val offsetDateTime = LocalDateTime.now()
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
            builder.setTimestamp(offsetDateTime)
            return this
        }

        fun addField(name: String, value: String, inline: Boolean = false): EmbedBuilder {
            builder.addField(WebhookEmbed.EmbedField(inline, name, value))
            return this
        }

        fun build(): WebhookEmbed {
            return builder.build()
        }
    }

    @JvmStatic
    fun sendEmbed(webhook: String, embed: WebhookEmbed, threadId: Long? = null) {
        val client = if (threadId != null) {
            WebhookClientBuilder(webhook).setThreadId(threadId).build()
        } else {
            WebhookClientBuilder(webhook).build()
        }

        client.send(embed)
    }

    @JvmStatic
    fun sendFile(webhook: String, file: File, filename: String? = null, content: String? = null, embed: WebhookEmbed? = null, threadId: Long? = null) {
        val client = if (threadId != null) {
            WebhookClientBuilder(webhook).setThreadId(threadId).build()
        } else {
            WebhookClientBuilder(webhook).build()
        }

        val messageBuilder = WebhookMessageBuilder()
        if (content != null) {
            messageBuilder.setContent(content)
        }
        if (embed != null) {
            messageBuilder.addEmbeds(embed)
        }

        messageBuilder.addFile(filename ?: file.name, file)
        val message = messageBuilder.build()

        client.send(message)
    }

    @JvmStatic
    fun sendFileData(webhook: String, fileData: ByteArray, filename: String, content: String? = null, embed: WebhookEmbed? = null, threadId: Long? = null) {
        val client = if (threadId != null) {
            WebhookClientBuilder(webhook).setThreadId(threadId).build()
        } else {
            WebhookClientBuilder(webhook).build()
        }

        val messageBuilder = WebhookMessageBuilder()
        if (content != null) {
            messageBuilder.setContent(content)
        }
        if (embed != null) {
            messageBuilder.addEmbeds(embed)
        }

        messageBuilder.addFile(filename, fileData)
        val message = messageBuilder.build()

        client.send(message)
    }

    @JvmStatic
    fun sendFileStream(webhook: String, inputStream: InputStream, filename: String, content: String? = null, embed: WebhookEmbed? = null, threadId: Long? = null) {
        val client = if (threadId != null) {
            WebhookClientBuilder(webhook).setThreadId(threadId).build()
        } else {
            WebhookClientBuilder(webhook).build()
        }

        val messageBuilder = WebhookMessageBuilder()
        if (content != null) {
            messageBuilder.setContent(content)
        }
        if (embed != null) {
            messageBuilder.addEmbeds(embed)
        }

        messageBuilder.addFile(filename, inputStream)
        val message = messageBuilder.build()

        client.send(message)
    }
}