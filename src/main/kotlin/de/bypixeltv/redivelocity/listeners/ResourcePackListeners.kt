package de.bypixeltv.redivelocity.listeners

import com.velocitypowered.api.event.Continuation
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import de.bypixeltv.redivelocity.config.Config
import jakarta.inject.Inject
import jakarta.inject.Singleton
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Singleton
class ResourcePackListeners @Inject constructor(
    private val proxy: ProxyServer,
    private val config: Config
) {

    private val miniMessage = MiniMessage.miniMessage()
    private val packStatusFutures = ConcurrentHashMap<Player, CompletableFuture<PlayerResourcePackStatusEvent.Status>>()

    private fun createPackRequest(player: Player): ResourcePackInfo {
        return proxy.createResourcePackBuilder(config.resourcepack.resourcepackUrl)
            .setId(player.uniqueId)
            .setPrompt(config.resourcepack.let { miniMessage.deserialize(it.resourcepackMessage) })
            .build()
    }

    @Subscribe
    fun onConfigurationFinish(e: PlayerFinishConfigurationEvent, continuation: Continuation) {
        println("Entered configuration finish event")

        val player = e.player
        val future = CompletableFuture<PlayerResourcePackStatusEvent.Status>()
        packStatusFutures[player] = future

        val pack = createPackRequest(player)
        player.sendResourcePacks(pack)
        println("Sending resource pack download request of player ${player.uniqueId}.")

        future.whenComplete { status, _ ->
            when (status) {
                PlayerResourcePackStatusEvent.Status.SUCCESSFUL -> {
                    println("Successfully finished resource pack download of player ${player.uniqueId}.")
                }
                PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD -> {
                    println("Failed resource pack download of player ${player.uniqueId}.")
                }
                PlayerResourcePackStatusEvent.Status.DECLINED -> {
                    println("Player ${player.uniqueId} declined the resource pack.")
                }
                PlayerResourcePackStatusEvent.Status.ACCEPTED -> {
                    println("Player ${player.uniqueId} accepted the resource pack.")
                }
                PlayerResourcePackStatusEvent.Status.DOWNLOADED -> {
                    println("Player ${player.uniqueId} downloaded the resource pack.")
                }
                PlayerResourcePackStatusEvent.Status.INVALID_URL -> {
                    println("Invalid URL for resource pack for player ${player.uniqueId}.")
                }
                PlayerResourcePackStatusEvent.Status.FAILED_RELOAD -> {
                    println("Failed to reload resource pack for player ${player.uniqueId}.")
                }
                PlayerResourcePackStatusEvent.Status.DISCARDED -> {
                    println("Player ${player.uniqueId} discarded the resource pack.")
                }
                else -> {
                    println("Unknown resource pack status for player ${player.uniqueId}.")
                }
            }
            continuation.resume()
        }
    }

    @Subscribe
    fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val player = event.player
        val userId = player.uniqueId

        packStatusFutures[player]?.complete(event.status)
        packStatusFutures.remove(player)
    }
}