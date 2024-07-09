package de.bypixeltv.redivelocity.listeners

import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import de.bypixeltv.redivelocity.config.Config
import net.kyori.adventure.resource.ResourcePackCallback
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.concurrent.CompletableFuture

class ResourcePackListeners @Inject constructor(private val config: Config, private val proxy: ProxyServer) {

    private val miniMessage = MiniMessage.miniMessage()

    private fun createPackRequest(player: Player, callback: ResourcePackCallback): ResourcePackInfo {
        return proxy.createResourcePackBuilder(config.resourcepackUrl)
            .setId(player.uniqueId)
            .setPrompt(miniMessage.deserialize(config.resourcepackMessage))
            .build()
    }

    @Subscribe
    fun onConfigurationFinish(e: PlayerFinishConfigurationEvent) : EventTask {
        println("Entered configuration finish event")

        // store a new countdown latch for this player
        val userId = e.player.uniqueId
        val future = CompletableFuture<Boolean>()

        // assemble the resource pack and
        val pack = createPackRequest(
            e.player,
            callback = ResourcePackCallback.onTerminal(
                { _, _ ->
                    // decrease the latch as it was successful
                    future.complete(true)

                    // inform about successful load
                    println("Successfully finished resource pack download of player $userId.")
                },
                { _, _ ->
                    // remove the latch as the operation failed
                    future.complete(false)

                    // inform about failure
                    println("Failed resource pack download of player $userId.")
                },
            ),
        )

        // send resource pack request
        e.player.sendResourcePacks(pack)
        println("Sending resource pack download request of player $userId.")

        // await the processing
        return EventTask.resumeWhenComplete(future)
    }
}