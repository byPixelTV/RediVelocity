package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ServerSwitchListener {

    private final RediVelocity rediVelocity;
    private final Config config;
    private final RedisController redisController;

    @Inject
    public ServerSwitchListener(RediVelocity rediVelocity, Config config, RedisController redisController) {
        this.rediVelocity = rediVelocity;
        this.config = config;
        this.redisController = redisController;
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        var player = event.getPlayer();
        var previousServerName = event.getPreviousServer().map(server -> server.getServerInfo().getName()).orElse("null");
        var redisConfig = config.getRedis();

        redisController.sendServerSwitchMessage(
                "serverSwitch",
                rediVelocity.getProxyId(),
                player.getUsername(),
                player.getUniqueId().toString(),
                player.getClientBrand(),
                player.getRemoteAddress().toString().split(":")[0].substring(1),
                event.getServer().getServerInfo().getName() != null ? event.getServer().getServerInfo().getName() : "null",
                previousServerName,
                redisConfig.getChannel()
        );

        redisController.setHashField("rv-players-server", player.getUniqueId().toString(), event.getServer().getServerInfo().getName());
    }
}