package de.bypixeltv.redivelocity.pubsub;

import com.velocitypowered.api.proxy.ProxyServer;
import de.bypixeltv.redivelocity.jedisWrapper.RedisManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.json.JSONObject;

import java.util.List;
import java.util.UUID;

@Singleton
public class MessageListener {

    private static final List<String> channels = List.of("redivelocity-kick", "redivelocity-heartbeat");

    private final RedisManager redisManager;
    private final ProxyServer proxyServer;

    @Inject
    public MessageListener(RedisManager redisManager, ProxyServer proxyServer) {
        this.redisManager = redisManager;
        this.proxyServer = proxyServer;
        init();
    }

    private void init() {
        redisManager.subscribe(channels, (pattern, channel, msg) -> {
            if ("redivelocity-kick".equals(channel)) {
                JSONObject message = new JSONObject(msg);
                String messagesString = message.getString("messages");
                JSONObject data = new JSONObject(messagesString);
                if (data.has("uuid") && data.has("reason")) {
                    UUID uuid = UUID.fromString(data.getString("uuid"));
                    String reason = data.getString("reason");

                    proxyServer.getPlayer(uuid).ifPresent(player -> player.disconnect(MiniMessage.miniMessage().deserialize(reason)));
                }
            }
        });
    }
}