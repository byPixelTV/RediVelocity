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
import de.bypixeltv.redivelocity.services.HeartbeatCheckService;
import de.bypixeltv.redivelocity.services.HeartbeatService;
import de.bypixeltv.redivelocity.services.PlayerCalcService;
import de.bypixeltv.redivelocity.utils.CloudUtils;
import de.bypixeltv.redivelocity.utils.ProxyIdGenerator;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIVelocityConfig;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@Plugin(id = "redivelocity", name = "RediVelocity", version = "1.1.1-Beta", description = "A fast, modern and clean alternative to RedisBungee on Velocity.", authors = {"byPixelTV"}, url = "https://github.com/byPixelTV/RediVelocity")
public class RediVelocity {

    private static final String RV_PLAYERS_NAME = "rv-players-name";
    private static final String RV_GLOBAL_PLAYERCOUNT = "rv-global-playercount";
    private static final String RV_PROXIES = "rv-proxies";
    private static final String RV_PROXY_VOTES = "rv-proxy-votes";
    private static final String RV_PROXY_LEADER = "rv-proxy-leader";

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

    private ScheduledTask globalPlayerCountTask;
    private ScheduledTask leaderElectionTask;

    @Inject
    public RediVelocity(
            ProxyServer proxy,
            ProxyIdGenerator proxyIdGenerator,
            UpdateManager updateManager,
            Provider<RediVelocityCommand> rediVelocityCommandProvider,
            RedisController redisController,
            RediVelocityLogger rediVelocityLogger
    ) {
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

    public void calculateGlobalPlayers() {
        globalPlayerCountTask = this.proxy.getScheduler().buildTask(this, () -> {
            List<Integer> proxyPlayersMap = redisController.getAllHashValues("rv-proxy-players").stream()
                    .map(Integer::parseInt)
                    .toList();
            int sum = proxyPlayersMap.stream().mapToInt(Integer::intValue).sum();
            redisController.setString(RV_GLOBAL_PLAYERCOUNT, String.valueOf(sum));
        }).repeat(5, TimeUnit.SECONDS).schedule();
    }

    public void startLeaderElection() {
        leaderElectionTask = this.proxy.getScheduler().buildTask(this, () -> {
            Set<String> activeProxies = redisController.getAllHashFields(RV_PROXIES);
            if (activeProxies.isEmpty()) {
                return;
            }

            String previousVote = redisController.getHashField(RV_PROXY_VOTES, proxyId);
            if (previousVote != null) {
                redisController.deleteHashField(RV_PROXY_VOTES, proxyId);
            }

            String voteFor;
            if (activeProxies.size() > 1) {
                List<String> otherProxies = activeProxies.stream()
                        .filter(proxy -> !proxy.equals(proxyId))
                        .toList();
                voteFor = otherProxies.get(new SecureRandom().nextInt(otherProxies.size()));
            } else {
                voteFor = proxyId;
            }

            redisController.setHashField(RV_PROXY_VOTES, proxyId, voteFor);

            Map<String, String> allVotes = redisController.getHashValuesAsPair(RV_PROXY_VOTES);
            Map<String, Long> voteCount = allVotes.values().stream()
                    .collect(Collectors.groupingBy(proxy -> proxy, Collectors.counting()));

            Map.Entry<String, Long> leader = voteCount.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 2)
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);

            if (leader == null) {
                leader = voteCount.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElse(null);
            }

            String newLeader;
            if (leader != null) {
                newLeader = leader.getKey();
            } else if (!activeProxies.isEmpty()) {
                newLeader = activeProxies.iterator().next();
            } else {
                return;
            }

            redisController.setString(RV_PROXY_LEADER, newLeader);

            if (newLeader.equals(proxyId)) {
                rediVelocityLogger.sendLogs("This proxy (" + proxyId + ") is now the leader.");
            }
        }).repeat(15, TimeUnit.SECONDS).schedule();
    }

    public void stop() {
        if (Objects.nonNull(globalPlayerCountTask)) {
            globalPlayerCountTask.cancel();
        }

        if (Objects.nonNull(leaderElectionTask)) {
            leaderElectionTask.cancel();
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        redisController.setString("rv-init-process", "true");

        configLoader.load();
        Config config = configLoader.getConfig();
        jsonFormat = String.valueOf(config.isJsonFormat());

        proxyId = config.getCloud().isEnabled() ?
                (CloudUtils.getServiceName(config.getCloud().getCloudSystem()) != null ? CloudUtils.getServiceName(config.getCloud().getCloudSystem()) : proxyIdGenerator.generate()) :
                proxyIdGenerator.generate();

        if (Objects.equals(proxyId, "Proxy-1")) {
            redisController.deleteHash("rv-proxy-players");
            redisController.deleteHash("rv-players-proxy");
            redisController.deleteHash("rv-proxy-heartbeat");
            redisController.deleteHash("rv-players-server");
            redisController.deleteHash("rv-proxies");
            redisController.deleteString("rv-global-playercount");
        }

        proxy.getScheduler().buildTask(this, () -> {
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
                rediVelocityLogger.sendConsoleMessage("<yellow>The <aqua>update checker</aqua> is disabled because you are using a <aqua>beta build</aqua> of <aqua>RediVelocity!</aqua></yellow>");
            }

            proxy.getEventManager().register(this, new ServerSwitchListener(this, config, redisController, rediVelocityLogger));
            proxy.getEventManager().register(this, new PostLoginListener(this, config, redisController, rediVelocityLogger, proxy));
            proxy.getEventManager().register(this, new DisconnectListener(config, redisController, this, rediVelocityLogger, proxy));
            // proxy.getEventManager().register(this, new ResourcePackListeners(proxy, config));

            if (config.isPlayerCountSync()) {
                proxy.getEventManager().register(this, new ProxyPingListener(redisController));
            }

            new MessageListener(redisManager, this.proxy);

            rediVelocityCommandProvider.get().register();

            new PlayerCalcService(redisController, proxyId, rediVelocityLogger, this, proxy).startCalc();

            calculateGlobalPlayers();
            startLeaderElection();

            new HeartbeatService(redisController, proxyId, rediVelocityLogger, this).startHeartbeat();
            new HeartbeatCheckService(redisController, proxyId, rediVelocityLogger, this).startHeartbeatCheck();

            if (config.getJoingate().getAllowBedrockClients()) {
                if (!config.getJoingate().getFloodgateHook()) {
                    rediVelocityLogger.sendErrorLogs("You currently allow Bedrock clients to connect, but the Floodgate hook is disabled, please enable the Floodgate hook in the config");
                } else {
                    // check if geyser and floodgate are installed
                    if (proxy.getPluginManager().getPlugin("floodgate").isEmpty() && proxy.getPluginManager().getPlugin("geyser").isEmpty()) {
                        rediVelocityLogger.sendErrorLogs("You currently allow Bedrock clients to connect, but Floodgate and GeyserMC are <color:#ff0000>NOT</color> installed, you should fix this issue.");
                    }
                }
            }
        }).delay(2, TimeUnit.SECONDS).schedule();

        redisController.deleteString("rv-init-process");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        stop();

        redisController.deleteHashField(RV_PROXY_VOTES, proxyId);

        String currentLeader = redisController.getString(RV_PROXY_LEADER);
        if (proxyId.equals(currentLeader)) {
            Set<String> activeProxies = redisController.getAllHashFields(RV_PROXIES);
            activeProxies.remove(proxyId);

            if (!activeProxies.isEmpty()) {
                List<String> otherProxies = new ArrayList<>(activeProxies);
                String newLeader = otherProxies.get(new SecureRandom().nextInt(otherProxies.size()));
                redisController.setString(RV_PROXY_LEADER, newLeader);
                rediVelocityLogger.sendLogs("New proxy leader selected (this proxy (the current leader) died): " + newLeader);
            } else {
                redisController.deleteString(RV_PROXY_LEADER);
            }
        }

        redisController.deleteHashField(RV_PROXIES, proxyId);
        redisController.deleteHashField("rv-proxy-players", proxyId);

        if (redisController.getAllHashFields(RV_PROXIES).isEmpty() || redisController.getAllHashFields(RV_PROXIES).size() == 1) {
            redisController.deleteHash(RV_PLAYERS_NAME);
            redisController.deleteHash("rv-proxy-players");
            redisController.deleteHash("rv-proxy-heartbeat");
            redisController.deleteString("rv-proxies-counter");
        }

        if (!redisController.exists(RV_PROXIES)) {
            redisController.deleteHash(RV_GLOBAL_PLAYERCOUNT);
            redisController.deleteHash(RV_PLAYERS_NAME);
        }
        redisController.shutdown();
    }
}