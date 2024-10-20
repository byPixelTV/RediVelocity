package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.managers.RedisController;
import de.bypixeltv.redivelocity.RediVelocity;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Set;

@Singleton
public class DisconnectListener {

    private final RediVelocity rediVelocity;
    private final Config config;
    private final RedisController redisController;
    private final String proxyId;

    @Inject
    public DisconnectListener(RediVelocity rediVelocity, Config config) {
        this.rediVelocity = rediVelocity;
        this.config = config;
        this.redisController = rediVelocity.getRedisController();
        this.proxyId = rediVelocity.getProxyId();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onDisconnectEvent(DisconnectEvent event) {
        var player = event.getPlayer();
        var redisConfig = config.getRedis();

        redisController.sendJsonMessage(
                "disconnect",
                rediVelocity.getProxyId(),
                player.getUsername(),
                player.getUniqueId().toString(),
                player.getClientBrand(),
                player.getRemoteAddress().toString().split(":")[0].substring(1),
                redisConfig.getChannel()
        );

        var players = redisController.getHashField("rv-proxy-players", proxyId);
        if (players != null) {
            int playerCount = Integer.parseInt(players);
            if (playerCount <= 0) {
                redisController.setHashField("rv-proxy-players", proxyId, "0");
            } else {
                redisController.setHashField("rv-proxy-players", proxyId, String.valueOf(playerCount - 1));
            }
        } else {
            redisController.setHashField("rv-proxy-players", proxyId, "0");
        }

        Map<String, String> proxyPlayersMap = redisController.getHashValuesAsPair("rv-proxy-players");
        int sum = proxyPlayersMap.values().stream()
                .mapToInt(Integer::parseInt)
                .sum();
        redisController.setString("rv-global-playercount", String.valueOf(sum));

        redisController.deleteHashField("rv-players-proxy", player.getUniqueId().toString());
        redisController.deleteHashField("rv-players-name", player.getUniqueId().toString());
        redisController.setHashField("rv-players-lastseen", player.getUniqueId().toString(), String.valueOf(System.currentTimeMillis()));
        redisController.deleteHashField("rv-players-server", player.getUniqueId().toString());
    }
}