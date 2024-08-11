package de.bypixeltv.redivelocity.commands

import com.velocitypowered.api.proxy.ProxyServer
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.config.ConfigLoader
import de.bypixeltv.redivelocity.utils.MojangUtils
import de.bypixeltv.redivelocity.utils.asDateString
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.*
import net.kyori.adventure.text.minimessage.MiniMessage
import java.util.*
import kotlin.jvm.optionals.getOrNull

@Singleton
class RediVelocityCommand @Inject constructor(
    rediVelocityProvider: Provider<RediVelocity>,
    private val proxy: ProxyServer,
    private val mojangUtilsProvider: Provider<MojangUtils>
) {

    private val configLoader: ConfigLoader = ConfigLoader("plugins/redivelocity/config.yml").apply { load() }

    private val config = configLoader.config

    private val miniMessage = MiniMessage.miniMessage()
    private val prefix = config?.messages?.prefix

    private val redisController = rediVelocityProvider.get().getRedisController()

    private val mojangUtils: MojangUtils
        get() = mojangUtilsProvider.get()

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("UNUSED")
    fun register() {
        CommandAPICommand("redivelocity")
            .withAliases("rv", "rediv", "redisvelocity", "redisv")
            .withPermission("redivelocity.admin")
            .withSubcommands(
                CommandAPICommand("player")
                    .withSubcommands(
                        CommandAPICommand("proxy")
                            .withArguments(StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.player.proxy")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerProxy = redisController.getHashField("rv-players-proxy",
                                    mojangUtils.getUUID(playerName).toString()
                                )
                                if (playerProxy != null) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is connected to proxy: <aqua>$playerProxy</aqua></gray>"))
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is <red>offline</red>.</gray>"))
                                }
                            }),
                        CommandAPICommand("lastseen")
                            .withArguments(StringArgument("playerLastSeen").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.player.lastseen")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val lastSeen = redisController.getHashField("rv-players-lastseen", mojangUtils.getUUID(playerName).toString())
                                val isOnline: Boolean =
                                    redisController.getHashField("rv-players-name", mojangUtils.getUUID(playerName).toString())?.contains(playerName) == true
                                if (!isOnline) {
                                    if (lastSeen != null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> was last seen <aqua>${lastSeen.toLong().asDateString()}</aqua>.</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> was never seen before.</gray>"))
                                    }
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is currently <green>online</green>.</gray>"))
                                }
                            }),
                        CommandAPICommand("ip")
                            .withArguments(StringArgument("playerIp").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.player.ip")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerIp = redisController.getHashField("rv-players-ip", mojangUtils.getUUID(playerName).toString())
                                if (playerIp != null) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is connected with IP: <aqua><hover:show_text:'<aqua>Click to copy</aqua>'><click:copy_to_clipboard:$playerIp>$playerIp</click></hover></aqua></gray>"))
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is <red>offline</red>.</gray>"))
                                }
                            }),
                        CommandAPICommand("uuid")
                            .withArguments(StringArgument("playerUuid").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.player.uuid")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerUuid = mojangUtils.getUUID(playerName).toString()
                                sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> has the UUID: <aqua><hover:show_text:'<aqua>Click to copy</aqua>'><click:copy_to_clipboard:$playerUuid>$playerUuid</click></hover></gray>"))
                            }),
                        CommandAPICommand("server")
                            .withArguments(StringArgument("playerUuid").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.player.server")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerServer = redisController.getHashField("rv-players-server", mojangUtils.getUUID(playerName).toString())
                                if (playerServer != null) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is connected to server: <aqua>$playerServer</aqua></gray>"))
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> is  currently <red>not</red> connected to a server.</gray>"))
                                }
                            })
                    ),
                CommandAPICommand("proxy")
                    .withSubcommands(
                        CommandAPICommand("list")
                            .withPermission("redivelocity.admin.proxy.list")
                            .executes(CommandExecutor { sender, _ ->
                                val proxies = redisController.getList("rv-proxies")
                                val proxiesPrettyNames: MutableList<String> = mutableListOf()
                                proxies?.forEach { proxyId ->
                                    proxiesPrettyNames.add("$prefix <aqua>$proxyId</aqua> <dark_grey>(<grey>Players: </grey><aqua>${redisController.getHashField("rv-proxy-players", proxyId)}</aqua>)</dark_grey>")
                                }
                                val proxiesPrettyString = proxiesPrettyNames.joinToString(separator = "<br>")
                                if (proxies != null) {
                                    if (proxies.isEmpty()) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no connected proxies.</gray>"))
                                        return@CommandExecutor
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently connected proxies:<br>$proxiesPrettyString</gray>"))
                                    }
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no connected proxies.</gray>"))
                                    return@CommandExecutor
                                }
                            }),
                        CommandAPICommand("players")
                            .withOptionalArguments(StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getList("rv-proxies")
                            }))
                            .withPermission("redivelocity.admin.proxy.players")
                            .executes(CommandExecutor { sender, args ->
                                val proxyId = args.getOptional(0).getOrNull() as? String
                                val players = redisController.getAllHashValues("rv-players-name")
                                val playersPrettyNames: MutableList<String> = mutableListOf()
                                val playerNames: MutableList<String> = mutableListOf()
                                players?.forEach { player ->
                                    val playerProxy = redisController.getHashField("rv-players-proxy", mojangUtils.getUUID(player).toString())
                                    if (proxyId == null) {
                                        playerNames.add(player)
                                        playersPrettyNames.add("$prefix <aqua>$player</aqua> <dark_gray>(<aqua>$playerProxy</aqua>)</dark_gray>")
                                    } else {
                                        if (playerProxy == proxyId) {
                                            playerNames.add(player)
                                            playersPrettyNames.add("$prefix <aqua>$player</aqua>")
                                        }
                                    }
                                }
                                val playersPrettyString = playersPrettyNames.joinToString(separator = "<br>")
                                if (playerNames.isEmpty()) {
                                    if (proxyId == null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no players online.</gray>"))
                                        return@CommandExecutor
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no players online on proxy <aqua>$proxyId</aqua>.</gray>"))
                                        return@CommandExecutor
                                    }
                                } else {
                                    if (proxyId != null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently online players on proxy <aqua>$proxyId</aqua>:<br>$playersPrettyString</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently online players:<br>$playersPrettyString</gray>"))

                                    }
                                }
                            }),
                        CommandAPICommand("playercount")
                            .withOptionalArguments(StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getList("rv-proxies")
                            }))
                            .withPermission("redivelocity.admin.proxy.playercount")
                            .executes(CommandExecutor { sender, args ->
                                val proxyId = args.getOptional(0).getOrNull() as? String
                                if (proxyId == null) {
                                    val playerCount = redisController.getString("rv-global-playercount")
                                    if (playerCount != null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently <aqua>$playerCount</aqua> players online.</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no players online.</gray>"))
                                    }
                                } else {
                                    val playerCount = redisController.getHashField("rv-proxy-players", proxyId)
                                    if (playerCount != null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently <aqua>$playerCount</aqua> players online on proxy <aqua>$proxyId</aqua>.</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no players online on proxy <aqua>$proxyId</aqua>.</gray>"))
                                    }
                                }
                            }),
                        CommandAPICommand("servers")
                            .withPermission("redivelocity.admin.proxy.servers")
                            .executes(CommandExecutor { sender, _ ->
                                GlobalScope.launch {
                                    val proxyRegisteredServers = proxy.allServers
                                    val futures = proxyRegisteredServers.map { server ->
                                        async {
                                            try {
                                                val result = server.ping().get()
                                                "$prefix <color:#0dbf00>●</color> <aqua>${server.serverInfo.name}</aqua> <dark_gray>(<grey>Address: <aqua>${server.serverInfo.address}</aqua>, Playercount: <aqua>${server.playersConnected.size}</aqua>, Version: <aqua>${result.version.protocol}, ${result.version.name}</aqua></grey>)</dark_gray>"
                                            } catch (e: Exception) {
                                                "$prefix <color:#f00000>●</color> <aqua>${server.serverInfo.name}</aqua> <dark_gray>(<grey>Address: <aqua>${server.serverInfo.address}</aqua></grey>)</dark_gray>"
                                            }
                                        }
                                    }
                                    val proxyRegisteredServersPrettyNames = futures.awaitAll()
                                    val proxyRegisteredServersPrettyString = proxyRegisteredServersPrettyNames.joinToString(separator = "<br>")
                                    if (proxyRegisteredServers.isNotEmpty()) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently registered servers:<br>$proxyRegisteredServersPrettyString</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no registered servers.</gray>"))
                                    }
                                }
                            })
                    ),
                CommandAPICommand("blacklist")
                    .withSubcommands(
                        CommandAPICommand("add")
                            .withArguments(StringArgument("proxyBlacklistAdd").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getAllHashValues("rv-players-name")
                            }))
                            .withPermission("redivelocity.admin.proxy.blacklist.add")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerUUID = mojangUtils.getUUID(playerName).toString()
                                if (!mojangUtils.isValidUUID(playerUUID)) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <red>Failed to add player to the blacklist. Maybe the name you've entered is not correct?</red>"))
                                    return@CommandExecutor
                                } else {
                                    redisController.setHashField("rv-players-blacklist", playerUUID, redisController.getHashField("rv-players-ip", playerUUID) ?: "unknown-ip")
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>Added player <aqua>$playerName</aqua> to the blacklist.</gray>"))
                                }
                            }),
                        CommandAPICommand("remove")
                            .withArguments(StringArgument("proxyBlacklistAdd").replaceSuggestions(ArgumentSuggestions.stringCollection {
                                redisController.getHashValuesAsPair("rv-players-blacklist").map { (uuid) -> mojangUtils.getName(UUID.fromString(uuid)) ?: "unknown-name" }
                            }))
                            .withPermission("redivelocity.admin.proxy.blacklist.remove")
                            .executes(CommandExecutor { sender, args ->
                                val playerName = args[0] as String
                                val playerUUID = mojangUtils.getUUID(playerName).toString()
                                redisController.deleteHashField("rv-players-blacklist", playerUUID)
                                sender.sendMessage(miniMessage.deserialize("$prefix <gray>Removed player <aqua>$playerName</aqua> from the blacklist.</gray>"))
                            }),
                        CommandAPICommand("list")
                            .withPermission("redivelocity.admin.proxy.blacklist.list")
                            .executes(CommandExecutor { sender, _ ->
                                val blacklistedPlayers = redisController.getHashValuesAsPair("rv-players-blacklist")
                                val blacklistedPlayersPrettyNames: MutableList<String> = mutableListOf()
                                blacklistedPlayers.forEach { (playerUUID, ip) ->
                                    val playerName = mojangUtils.getName(UUID.fromString(playerUUID)) ?: "unknown-name"
                                    blacklistedPlayersPrettyNames.add("$prefix <aqua><hover:show_text:'<aqua>$playerUUID</aqua>'><click:copy_to_clipboard:$playerUUID>$playerName</click></hover></aqua> <dark_grey>(<aqua>IP: <hover:show_text:'<aqua>Click to copy ip</aqua>'><click:copy_to_clipboard:$ip>$ip</click></hover></aqua>)</dark_grey>")
                                }
                                val blacklistedPlayersPrettyString = blacklistedPlayersPrettyNames.joinToString(separator = "<br>")
                                if (blacklistedPlayers.isEmpty()) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no blacklisted players.</gray>"))
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently blacklisted players:<br>$blacklistedPlayersPrettyString</gray>"))
                                }
                            })
                    )
            )
            .register()
    }
}