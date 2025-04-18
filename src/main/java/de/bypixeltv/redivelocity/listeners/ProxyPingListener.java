package de.bypixeltv.redivelocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class ProxyPingListener {

    private final RedisController redisController;
    private final ExecutorService redisExecutor = Executors.newFixedThreadPool(5); // Dedizierter Thread-Pool

    @Inject
    public ProxyPingListener(RedisController redisController) {
        this.redisController = redisController;
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onProxyPing(ProxyPingEvent event) {
        CompletableFuture.supplyAsync(() -> redisController.getString("rv-global-playercount"), redisExecutor)
                .thenAccept(players -> {
                    var ping = event.getPing().asBuilder();
                    ping.onlinePlayers(players != null ? Integer.parseInt(players) : 0);
                    event.setPing(ping.build());
                })
                .exceptionally(ex -> {
                    ex.printStackTrace();
                    return null;
                });
    }
}