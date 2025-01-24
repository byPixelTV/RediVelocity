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

package de.bypixeltv.redivelocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import de.bypixeltv.redivelocity.commands.RediVelocityCommand;
import de.bypixeltv.redivelocity.config.Config;
import de.bypixeltv.redivelocity.config.ConfigLoader;
import de.bypixeltv.redivelocity.jedisWrapper.RedisController;
import de.bypixeltv.redivelocity.jedisWrapper.RedisManager;
import de.bypixeltv.redivelocity.jedisWrapper.UpdateManager;
import de.bypixeltv.redivelocity.listeners.DisconnectListener;
import de.bypixeltv.redivelocity.listeners.PostLoginListener;
import de.bypixeltv.redivelocity.listeners.ProxyPingListener;
import de.bypixeltv.redivelocity.listeners.ServerSwitchListener;
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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
@Plugin(id = "redivelocity", name = "RediVelocity", version = "1.1.1-Beta", description = "A fast, modern and clean alternative to RedisBungee on Velocity.", authors = {"byPixelTV"}, url = "https://github.com/byPixelTV/RediVelocity")
public class RediVelocity {

    private static final String RV_PLAYERS_NAME = "rv-players-name";
    private static final String RV_GLOBAL_PLAYERCOUNT = "rv-global-playercount";
    private static final String RV_PROXIES = "rv-proxies";

    public final ProxyServer proxy;
    private final ProxyIdGenerator proxyIdGenerator;
    private final UpdateManager updateManager;
    private final Provider<RediVelocityCommand> rediVelocityCommandProvider;
    private final RedisController redisController;
    private final RediVelocityLogger rediVelocityLogger;

    private final ConfigLoader configLoader;
    @Setter
    @Getter
    private String jsonFormat;
    @Getter
    private String proxyId;

    @Inject
    public RediVelocity(ProxyServer proxy,
                        ProxyIdGenerator proxyIdGenerator,
                        UpdateManager updateManager,
                        Provider<RediVelocityCommand> rediVelocityCommandProvider,
                        RedisController redisController,
                        RediVelocityLogger rediVelocityLogger) {
        this.proxy = proxy;
        this.proxyIdGenerator = proxyIdGenerator;
        this.updateManager = updateManager;
        this.rediVelocityCommandProvider = rediVelocityCommandProvider;
        this.rediVelocityLogger = rediVelocityLogger;

        // Provide a valid configuration file path
        this.configLoader = new ConfigLoader(rediVelocityLogger);

        // Load the configuration file
        CommandAPI.onLoad(new CommandAPIVelocityConfig(proxy, this).silentLogs(true).verboseOutput(true));
        this.configLoader.load();

        // Fetch the configuration and handle potential null value
        Config config = configLoader.getConfig();
        if (config == null) {
            rediVelocityLogger.sendErrorLogs("<red>Failed to load the configuration! Falling back to default configuration.</red>");
            config = new Config(); // Initialize with default config to prevent further null pointer issues
        }

        this.redisController = redisController;

        // Use the config safely
        this.jsonFormat = String.valueOf(config.isJsonFormat());
    }

    private ScheduledTask globalPlayerCountTask;

    public void calculateGlobalPlayers() {
        globalPlayerCountTask = this.proxy.getScheduler().buildTask(this, () -> {
            Map<String, String> proxyPlayersMap = redisController.getHashValuesAsPair(RV_PLAYERS_NAME);
            int sum = proxyPlayersMap.size();
            redisController.setString(RV_GLOBAL_PLAYERCOUNT, String.valueOf(sum));
        }).repeat(5, TimeUnit.SECONDS).schedule();
    }


    public void stop() {
        if (Objects.nonNull(globalPlayerCountTask)) {
            globalPlayerCountTask.cancel();
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        configLoader.load();
        Config config = configLoader.getConfig();
        jsonFormat = String.valueOf(config.isJsonFormat());

        proxyId = config.getCloud().isEnabled() ?
                (CloudUtils.getServiceName(config.getCloud().getCloudSystem()) != null ? CloudUtils.getServiceName(config.getCloud().getCloudSystem()) : proxyIdGenerator.generate()) :
                proxyIdGenerator.generate();

        CommandAPI.onEnable();

        Optional<PluginContainer> pluginContainer = proxy.getPluginManager().getPlugin("redivelocity");

        if (!redisController.exists(RV_PROXIES)) {
            redisController.deleteHash(RV_PROXIES);
            redisController.deleteHash("rv-proxy-players");
            redisController.deleteHash(RV_PLAYERS_NAME);
            redisController.deleteHash(RV_GLOBAL_PLAYERCOUNT);
        }

        redisController.setHashField(RV_PROXIES, proxyId, proxyId);
        redisController.setHashField("rv-proxy-players", proxyId, "0");
        if (redisController.getString(RV_GLOBAL_PLAYERCOUNT) == null) {
            redisController.setString(RV_GLOBAL_PLAYERCOUNT, "0");
        }
        rediVelocityLogger.sendLogs("Creating new Proxy with ID: " + proxyId);

        RedisManager redisManager = new RedisManager(rediVelocityLogger, redisController.getJedisPool());

        boolean isBeta = false;
        if (pluginContainer.isPresent()) {
            String version = pluginContainer.get().getDescription().getVersion().toString();
            if (version.contains("-")) {
                rediVelocityLogger.sendConsoleMessage("<yellow>This is a <color:#ff0000><b>BETA build,</b></color> things may not work as expected, please report any bugs on <aqua>GitHub</aqua></yellow>");
                rediVelocityLogger.sendConsoleMessage("<aqua><b>https://github.com/byPixelTV/RediVelocity/issues</b></aqua>");
                isBeta = true;
            }
        } else {
            rediVelocityLogger.sendErrorLogs("RediVelocity plugin not found (soo, this is really bad, please report this issue on GitHub)");
        }

        if (!isBeta) {
            updateManager.checkForUpdate();
        } else {
            rediVelocityLogger.sendConsoleMessage("<yellow>The <aqua>update checker</aqua> is disabled, because you are using a <aqua>beta build</aqua> of <aqua>RediVelocity!</aqua></yellow>");
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
                rediVelocityLogger.sendErrorLogs("You currently disallow Bedrock client to connect, but the Floodgate hook is disabled, please enable the Floodgate hook in the config");
            } else {
                // check if geyser and floodgate are installed
                if (proxy.getPluginManager().getPlugin("floodgate").isEmpty() && proxy.getPluginManager().getPlugin("geyser").isEmpty()) {
                    rediVelocityLogger.sendErrorLogs("You currently disallow Bedrock client to connect, but Floodgate and GeyserMC are <color:#ff0000>NOT</color> installed, you should fix this issue.");
                }
            }
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        stop();

        redisController.deleteHashField(RV_PROXIES, proxyId);
        redisController.deleteHashField("rv-proxy-players", proxyId);

        if (redisController.getAllHashFields(RV_PROXIES).isEmpty() || redisController.getAllHashFields(RV_PROXIES).size() == 1) {
            redisController.deleteHash(RV_PLAYERS_NAME);
        }

        if (!redisController.exists(RV_PROXIES)) {
            redisController.deleteHash(RV_GLOBAL_PLAYERCOUNT);
            redisController.deleteHash(RV_PLAYERS_NAME);
        }
        redisController.shutdown();
    }
}