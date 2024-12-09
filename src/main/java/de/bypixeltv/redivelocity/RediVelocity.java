package de.bypixeltv.redivelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import de.bypixeltv.redivelocity.commands.RediVelocityCommand;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.config.ConfigLoader;
import de.bypixeltv.redivelocity.listeners.*;
import de.bypixeltv.redivelocity.managers.RedisController;
import de.bypixeltv.redivelocity.managers.RedisManager;
import de.bypixeltv.redivelocity.managers.UpdateManager;
import de.bypixeltv.redivelocity.pubsub.MessageListener;
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class RediVelocity {

    public final ProxyServer proxy;
    private final ProxyIdGenerator proxyIdGenerator;
    private final UpdateManager updateManager;
    private final Provider<RediVelocityCommand> rediVelocityCommandProvider;
    private final RedisController redisController;
    private final Provider<RediVelocity> rediVelocityProvider = () -> this;

    private final MiniMessage miniMessages = MiniMessage.miniMessage();
    private final ConfigLoader configLoader;
    @Setter
    @Getter
    private String jsonFormat;
    @Getter
    private String proxyId;

    @Inject
    public RediVelocity(ProxyServer proxy, ProxyIdGenerator proxyIdGenerator,
                        UpdateManager updateManager, Provider<RediVelocityCommand> rediVelocityCommandProvider,
                        RedisController redisController) {
        this.proxy = proxy;
        this.proxyIdGenerator = proxyIdGenerator;
        this.updateManager = updateManager;
        this.rediVelocityCommandProvider = rediVelocityCommandProvider;
        this.redisController = redisController;

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

    public void sendConsoleMessage(String message) {
        this.proxy.getConsoleCommandSource().sendMessage(miniMessages.deserialize("<grey>[<aqua>RediVelocity</aqua>]</grey> " + message));
    }

    private ScheduledTask globalPlayerCountTask;

    public void calculateGlobalPlayers() {
        globalPlayerCountTask = this.proxy.getScheduler().buildTask(this, () -> {
            Map<String, String> proxyPlayersMap = redisController.getHashValuesAsPair("rv-players-name");
            int sum = proxyPlayersMap.size();
            redisController.setString("rv-global-playercount", String.valueOf(sum));
        }).repeat(5, TimeUnit.SECONDS).schedule();
    }


    public void stop() {
        if (Objects.nonNull(globalPlayerCountTask)) {
            globalPlayerCountTask.cancel();
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        sendLogs("RediVelocity initialization started");

        configLoader.load();
        Config config = configLoader.getConfig();
        jsonFormat = String.valueOf(config.isJsonFormat());

        redisController.initialize(rediVelocityProvider, config); // Use the existing instance

        CommandAPI.onEnable();

        proxyId = config.getCloud().isEnabled() ?
                (CloudUtils.getServiceName(config.getCloud().getCloudSystem()) != null ? CloudUtils.getServiceName(config.getCloud().getCloudSystem()) : proxyIdGenerator.generate()) :
                proxyIdGenerator.generate();

        if (!redisController.exists("rv-proxies")) {
            redisController.deleteHash("rv-proxies");
            redisController.deleteHash("rv-proxy-players");
            redisController.deleteHash("rv-players-name");
            redisController.deleteHash("rv-global-playercount");
        }

        redisController.setHashField("rv-proxies", proxyId, proxyId);
        redisController.setHashField("rv-proxy-players", proxyId, "0");
        if (redisController.getString("rv-global-playercount") == null) {
            redisController.setString("rv-global-playercount", "0");
        }
        sendLogs("Creating new Proxy with ID: " + proxyId);

        RedisManager redisManager = new RedisManager(redisController.getJedisPool());

        Optional<PluginContainer> pluginContainer = proxy.getPluginManager().getPlugin("redivelocity");
        boolean isBeta = false;
        if (pluginContainer.isPresent()) {
            String version = pluginContainer.get().getDescription().getVersion().toString();
            if (version.contains("-")) {
                sendConsoleMessage("<yellow>This is a <color:#ff0000><b>BETA build,</b></color> things may not work as expected, please report any bugs on <blue>GitHub</blue></yellow>");
                sendConsoleMessage("<blue><b>https://github.com/byPixelTV/RediVelocity/issues</b></blue>");
                isBeta = true;
            }
        } else {
            sendErrorLogs("RediVelocity plugin not found (soo, this is really bad, please report this issue on GitHub)");
        }

        if (!isBeta) {
            updateManager.checkForUpdate();
        } else {
            sendConsoleMessage("<yellow>The <blue>update checker</blue> is disabled, because you are using a <blue>beta build</blue> of <blue>RediVelocity!</blue></yellow>");
        }

        proxy.getEventManager().register(this, new ServerSwitchListener(this, config, redisController));
        proxy.getEventManager().register(this, new PostLoginListener(this, config, redisController));
        proxy.getEventManager().register(this, new DisconnectListener(config, redisController, this));
        // proxy.getEventManager().register(this, new ResourcePackListeners(proxy, config));

        if (config.isPlayerCountSync()) {
            proxy.getEventManager().register(this, new ProxyPingListener(redisController));
        }

        new MessageListener(redisManager, this.proxy);

        rediVelocityCommandProvider.get().register();

        calculateGlobalPlayers();

        if (config.getJoingate().getAllowBedrockClients()) {
            if (!config.getJoingate().getFloodgateHook()) {
                sendErrorLogs("You currently disallow Bedrock client to connect, but the Floodgate hook is disabled, please enable the Floodgate hook in the config");
            } else {
                // check if geyser and floodgate are installed
                if (proxy.getPluginManager().getPlugin("floodgate").isEmpty() && proxy.getPluginManager().getPlugin("geyser").isEmpty()) {
                    sendErrorLogs("You currently disallow Bedrock client to connect, but Floodgate and GeyserMC are <color:#ff0000>NOT</color> installed, you should fix this issue.");
                }
            }
        }

        sendLogs("RediVelocity initialization completed");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        sendLogs("RediVelocity shutdown started");

        stop();

        redisController.deleteHashField("rv-proxies", proxyId);
        redisController.deleteHashField("rv-proxy-players", proxyId);

        if (redisController.getAllHashFields("rv-proxies").isEmpty() || redisController.getAllHashFields("rv-proxies").size() == 1) {
            redisController.deleteHash("rv-players-name");
        }

        if (!redisController.exists("rv-proxies")) {
            redisController.deleteHash("rv-global-playercount");
            redisController.deleteHash("rv-players-name");
        }
        redisController.shutdown();
        sendLogs("RediVelocity shutdown completed");
    }
}