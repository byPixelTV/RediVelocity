/*
 * Copyright (c) 2025.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Continuation;
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

    @Subscribe()
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