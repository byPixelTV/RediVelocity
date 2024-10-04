package de.bypixeltv.redivelocity.commands;

import de.bypixeltv.redivelocity.RediVelocity;
import de.bypixeltv.redivelocity.config.ConfigLoader;
import de.bypixeltv.redivelocity.managers.RedisController;
import de.bypixeltv.redivelocity.utils.MojangUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.StringArgument;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class RediVelocityCommand {

    private final Provider<MojangUtils> mojangUtilsProvider;
    private final String prefix;
    private final MiniMessage miniMessage;
    private final RedisController redisController;

    @Inject
    public RediVelocityCommand(Provider<RediVelocity> rediVelocityProvider, Provider<MojangUtils> mojangUtilsProvider) {
        this.mojangUtilsProvider = mojangUtilsProvider;
        ConfigLoader configLoader = new ConfigLoader("plugins/redivelocity/config.yml");
        configLoader.load();
        this.prefix = configLoader.getConfig().getMessages().getPrefix();
        this.miniMessage = MiniMessage.miniMessage();
        RediVelocity rediVelocity = rediVelocityProvider.get();
        this.redisController = rediVelocity.getRedisController();
    }

    public void register() {
        new CommandAPICommand("redivelocity")
                .withAliases("rv", "rediv", "redisvelocity", "redisv")
                .withSubcommands(
                        new CommandAPICommand("player")
                                .withSubcommands(
                                        new CommandAPICommand("proxy")
                                                .withArguments(new StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection(info -> redisController.getAllHashValues("rv-players-name"))))
                                                .withPermission("redivelocity.admin.player.proxy")
                                                .executes((sender, args) -> {
                                                    String playerName = (String) args.get(0);
                                                    String playerProxy = redisController.getHashField("rv-players-proxy", mojangUtilsProvider.get().getUUID(playerName).toString());
                                                    if (playerProxy != null) {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> is connected to proxy: <aqua>" + playerProxy + "</aqua></gray>"));
                                                    } else {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> is <red>offline</red>.</gray>"));
                                                    }
                                                }),
                                        new CommandAPICommand("lastseen")
                                                .withArguments(new StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection(info -> redisController.getAllHashValues("rv-players-name"))))
                                                .withPermission("redivelocity.admin.player.lastseen")
                                                .executes((sender, args) -> {
                                                    String playerName = (String) args.get(0);
                                                    String lastSeen = redisController.getHashField("rv-players-lastseen", mojangUtilsProvider.get().getUUID(playerName).toString());
                                                    assert playerName != null;
                                                    boolean isOnline = redisController.getHashField("rv-players-name", mojangUtilsProvider.get().getUUID(playerName).toString()).contains(playerName);
                                                    if (!isOnline) {
                                                        if (lastSeen != null) {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> was last seen <aqua>" + lastSeen + "</aqua>.</gray>"));
                                                        } else {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> was never seen before.</gray>"));
                                                        }
                                                    } else {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> is currently <green>online</green>.</gray>"));
                                                    }
                                                }),
                                        new CommandAPICommand("ip")
                                                .withArguments(new StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection(info -> redisController.getAllHashValues("rv-players-name"))))
                                                .withPermission("redivelocity.admin.player.ip")
                                                .executes((sender, args) -> {
                                                    String playerName = (String) args.get(0);
                                                    String playerIp = redisController.getHashField("rv-players-ip", mojangUtilsProvider.get().getUUID(playerName).toString());
                                                    if (playerIp != null) {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> is connected with IP: <aqua><hover:show_text:'<aqua>Click to copy</aqua>'><click:copy_to_clipboard:" + playerIp + ">" + playerIp + "</click></hover></aqua></gray>"));
                                                    } else {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> is <red>offline</red>.</gray>"));
                                                    }
                                                }),
                                        new CommandAPICommand("uuid")
                                                .withArguments(new StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection(info -> redisController.getAllHashValues("rv-players-name"))))
                                                .withPermission("redivelocity.admin.player.uuid")
                                                .executes((sender, args) -> {
                                                    String playerName = (String) args.get(0);
                                                    String playerUuid = mojangUtilsProvider.get().getUUID(playerName).toString();
                                                    sender.sendMessage(miniMessage.deserialize(prefix + " <gray>The player <aqua>" + playerName + "</aqua> has the UUID: <aqua><hover:show_text:'<aqua>Click to copy</aqua>'><click:copy_to_clipboard:" + playerUuid + ">" + playerUuid + "</click></hover></aqua></gray>"));
                                                })
                                ),
                        new CommandAPICommand("proxy")
                                .withSubcommands(
                                        new CommandAPICommand("list")
                                                .withPermission("redivelocity.admin.proxy.list")
                                                .executes((sender, args) -> {
                                                    List<String> proxies = redisController.getList("rv-proxies");
                                                    List<String> proxiesPrettyNames = new ArrayList<>();
                                                    if (proxies != null) {
                                                        for (String proxyId : proxies) {
                                                            proxiesPrettyNames.add(prefix + " <aqua>" + proxyId + "</aqua> <dark_grey>(<grey>Players: </grey><aqua>" + redisController.getHashField("rv-proxy-players", proxyId) + "</aqua>)</dark_grey>");
                                                        }
                                                    }
                                                    String proxiesPrettyString = String.join("<br>", proxiesPrettyNames);
                                                    if (proxies != null && !proxies.isEmpty()) {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>Currently connected proxies:<br>" + proxiesPrettyString + "</gray>"));
                                                    } else {
                                                        sender.sendMessage(miniMessage.deserialize(prefix + " <gray>There are currently no connected proxies.</gray>"));
                                                    }
                                                }),
                                        new CommandAPICommand("playercount")
                                                .withArguments(new StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection(info -> redisController.getAllHashValues("rv-players-name"))))
                                                .withPermission("redivelocity.admin.proxy.playercount")
                                                .executes((sender, args) -> {
                                                    String proxyId = (String) args.get(0);
                                                    if (proxyId == null) {
                                                        String playerCount = redisController.getString("rv-global-playercount");
                                                        if (playerCount != null) {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>There are currently <aqua>" + playerCount + "</aqua> players online.</gray>"));
                                                        } else {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>There are currently no players online.</gray>"));
                                                        }
                                                    } else {
                                                        String playerCount = redisController.getHashField("rv-proxy-players", proxyId);
                                                        if (playerCount != null) {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>There are currently <aqua>" + playerCount + "</aqua> players online on proxy <aqua>" + proxyId + "</aqua>.</gray>"));
                                                        } else {
                                                            sender.sendMessage(miniMessage.deserialize(prefix + " <gray>There are currently no players online on proxy <aqua>" + proxyId + "</aqua>.</gray>"));
                                                        }
                                                    }
                                                })
                                )
                )
                .register();
    }
}
