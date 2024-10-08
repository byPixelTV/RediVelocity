package de.bypixeltv.redivelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import de.bypixeltv.redivelocity.commands.RediVelocityCommand;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.config.ConfigLoader;
import de.bypixeltv.redivelocity.listeners.DisconnectListener;
import de.bypixeltv.redivelocity.listeners.PostLoginListener;
import de.bypixeltv.redivelocity.listeners.ProxyPingListener;
import de.bypixeltv.redivelocity.listeners.ServerSwitchListener;
import de.bypixeltv.redivelocity.managers.RedisController;
import de.bypixeltv.redivelocity.managers.UpdateManager;
import de.bypixeltv.redivelocity.utils.CloudUtils;
import de.bypixeltv.redivelocity.utils.ProxyIdGenerator;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIVelocityConfig;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;

@Singleton
public class RediVelocity {

    public final ProxyServer proxy;
    private final ProxyIdGenerator proxyIdGenerator;
    private final UpdateManager updateManager;
    private final Provider<RediVelocityCommand> rediVelocityCommandProvider;

    private final MiniMessage miniMessages = MiniMessage.miniMessage();
    private final ConfigLoader configLoader;
    @Setter
    @Getter
    private String jsonFormat;
    @Getter
    private String proxyId;
    @Getter
    private RedisController redisController;

    @Inject
    public RediVelocity(ProxyServer proxy, ProxyIdGenerator proxyIdGenerator,
                        UpdateManager updateManager, Provider<RediVelocityCommand> rediVelocityCommandProvider) {
        this.proxy = proxy;
        this.proxyIdGenerator = proxyIdGenerator;
        this.updateManager = updateManager;
        this.rediVelocityCommandProvider = rediVelocityCommandProvider;

        this.configLoader = new ConfigLoader("plugins/redivelocity/config.yml");
        this.configLoader.load();
        Config config = configLoader.getConfig();
        this.jsonFormat = String.valueOf(config.isJsonFormat());

        CommandAPI.onLoad(new CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true));
        sendLogs("RediVelocity plugin loaded");
    }

    public void sendLogs(String message) {
        this.proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <yellow>" + message + "</yellow>"));
    }

    public void sendErrorLogs(String message) {
        this.proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> <red>" + message + "</red>"));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        sendLogs("Proxy initialization started");

        configLoader.load();
        Config config = configLoader.getConfig();
        jsonFormat = String.valueOf(config.isJsonFormat());

        redisController = new RedisController(this, config);
        CommandAPI.onEnable();

        proxyId = config.getCloud().isEnabled() ?
                (CloudUtils.getServiceName(config.getCloud().getCloudSystem()) != null ? CloudUtils.getServiceName(config.getCloud().getCloudSystem()) : proxyIdGenerator.generate()) :
                proxyIdGenerator.generate();

        redisController.addToList("rv-proxies", new String[]{proxyId});
        redisController.setHashField("rv-proxy-players", proxyId, "0");
        if (redisController.getString("rv-global-playercount") == null) {
            redisController.setString("rv-global-playercount", "0");
        }
        sendLogs("Creating new Proxy with ID: " + proxyId);

        Optional<PluginContainer> pluginContainer = proxy.getPluginManager().getPlugin("redivelocity");
        if (pluginContainer.isPresent()) {
            String version = pluginContainer.get().getDescription().getVersion().toString();
            if (version.contains("-")) {
                sendLogs("This is a BETA build, things may not work as expected, please report any bugs on GitHub");
                sendLogs("https://github.com/byPixelTV/RediVelocity/issues");
            }
        } else {
            // Handle the case where the plugin is not present
            sendErrorLogs("RediVelocity plugin not found");
        }

        updateManager.checkForUpdate();

        proxy.getEventManager().register(this, new ServerSwitchListener(this, config));
        proxy.getEventManager().register(this, new PostLoginListener(this, config));
        proxy.getEventManager().register(this, new DisconnectListener(this, config));

        if (config.isPlayerCountSync()) {
            proxy.getEventManager().register(this, new ProxyPingListener(this));
        }

        rediVelocityCommandProvider.get().register();
        sendLogs("Proxy initialization completed");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        sendLogs("Proxy shutdown started");
        redisController.removeFromListByValue("rv-proxies", proxyId);
        redisController.deleteHashField("rv-proxy-players", proxyId);
        redisController.deleteHash("rv-players-name");
        redisController.deleteHash("rv-" + proxyId + "-servers-servers");
        redisController.deleteHash("rv-" + proxyId + "-servers-players");
        redisController.deleteHash("rv-" + proxyId + "-servers-playercount");
        redisController.deleteHash("rv-" + proxyId + "-servers-address");

        if (redisController.getList("rv-proxies").isEmpty()) {
            redisController.deleteHash("rv-proxy-players");
            redisController.deleteString("rv-global-playercount");
        }
        redisController.shutdown();
        sendLogs("Proxy shutdown completed");
    }

}