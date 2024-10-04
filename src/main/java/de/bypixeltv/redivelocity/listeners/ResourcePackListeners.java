package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import de.bypixeltv.redivelocity.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class ResourcePackListeners {

    private final ProxyServer proxy;
    private final Config config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ConcurrentHashMap<Player, CompletableFuture<PlayerResourcePackStatusEvent.Status>> packStatusFutures = new ConcurrentHashMap<>();

    @Inject
    public ResourcePackListeners(ProxyServer proxy, Config config) {
        this.proxy = proxy;
        this.config = config;
    }

    private ResourcePackInfo createPackRequest(Player player) {
        return proxy.createResourcePackBuilder(config.getResourcepack().getResourcepackUrl())
                .setId(player.getUniqueId())
                .setPrompt(miniMessage.deserialize(config.getResourcepack().getResourcepackMessage()))
                .build();
    }

    @Subscribe
    public void onConfigurationFinish(PlayerFinishConfigurationEvent e, Continuation continuation) {
        System.out.println("Entered configuration finish event");

        Player player = e.player();
        CompletableFuture<PlayerResourcePackStatusEvent.Status> future = new CompletableFuture<>();
        packStatusFutures.put(player, future);

        ResourcePackInfo pack = createPackRequest(player);
        player.sendResourcePacks(pack);
        System.out.println("Sending resource pack download request of player " + player.getUniqueId() + ".");

        future.whenComplete((status, throwable) -> {
            switch (status) {
                case SUCCESSFUL:
                    System.out.println("Successfully finished resource pack download of player " + player.getUniqueId() + ".");
                    break;
                case FAILED_DOWNLOAD:
                    System.out.println("Failed resource pack download of player " + player.getUniqueId() + ".");
                    break;
                case DECLINED:
                    System.out.println("Player " + player.getUniqueId() + " declined the resource pack.");
                    break;
                case ACCEPTED:
                    System.out.println("Player " + player.getUniqueId() + " accepted the resource pack.");
                    break;
                case DOWNLOADED:
                    System.out.println("Player " + player.getUniqueId() + " downloaded the resource pack.");
                    break;
                case INVALID_URL:
                    System.out.println("Invalid URL for resource pack for player " + player.getUniqueId() + ".");
                    break;
                case FAILED_RELOAD:
                    System.out.println("Failed to reload resource pack for player " + player.getUniqueId() + ".");
                    break;
                case DISCARDED:
                    System.out.println("Player " + player.getUniqueId() + " discarded the resource pack.");
                    break;
                default:
                    System.out.println("Unknown resource pack status for player " + player.getUniqueId() + ".");
                    break;
            }
            continuation.resume();
        });
    }

    @Subscribe
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        packStatusFutures.get(player).complete(event.getStatus());
        packStatusFutures.remove(player);
    }
}