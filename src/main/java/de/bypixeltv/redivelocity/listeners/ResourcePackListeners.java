package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerFinishConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import de.bypixeltv.redivelocity.config.Config;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class ResourcePackListeners {
    private final ProxyServer proxy;
    private final Config config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Inject
    public ResourcePackListeners(ProxyServer proxy, Config config) {
        this.proxy = proxy;
        this.config = config;
    }

    private ResourcePackInfo createPackRequest(Player player) {
        return proxy.createResourcePackBuilder(config.getResourcepack().getResourcepackUrl())
                .setId(player.getUniqueId())
                .setPrompt(miniMessage.deserialize(config.getResourcepack().getResourcepackMessage()))
                .setShouldForce(config.getResourcepack().isForceResourcepack())
                .build();
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onConfigurationFinish(PlayerFinishConfigurationEvent event, Continuation continuation) {
        Player player = event.player();
        ResourcePackInfo pack = createPackRequest(player);
        player.sendResourcePacks(pack);

        scheduler.scheduleAtFixedRate(() -> {
            if (!player.getAppliedResourcePacks().isEmpty()) {
                continuation.resume();
                scheduler.shutdown();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
}