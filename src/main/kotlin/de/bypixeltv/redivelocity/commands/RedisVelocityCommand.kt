package de.bypixeltv.redivelocity.commands

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.util.UuidUtils
import de.bypixeltv.redivelocity.RediVelocity
import de.bypixeltv.redivelocity.managers.RedisController
import de.bypixeltv.redivelocity.utils.DateUtils.asDateString
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import net.kyori.adventure.text.minimessage.MiniMessage
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import kotlin.jvm.optionals.getOrNull

class RedisVelocityCommand(private val rediVelocity: RediVelocity, private val proxy: ProxyServer, private val redisController: RedisController) {

    private val miniMessage = MiniMessage.miniMessage()
    private val prefix = "<dark_gray>[<aqua>ℹ</aqua>]</dark_gray> <color:#0079FF>⌞RediVelocity⌝</color> <dark_gray>◘</dark_gray>"

    private fun getUUID(username: String): String? {
        try {
            val url = URI("https://api.mojang.com/users/profiles/minecraft/$username")
            val reader = BufferedReader(InputStreamReader(url.toURL().openStream()))
            val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
            var uuid = jsonObject["id"].asString
            uuid = UuidUtils.fromUndashed(uuid).toString()
            return uuid
        } catch (e: IOException) {
            rediVelocity.sendErrorLogs("Failed to get UUID for $username!")
        }
        return null
    }

    @Suppress("UNUSED")
    val cmd = CommandAPICommand("redivelocity")
        .withAliases("rv", "rediv", "redisvelocity", "redisv")
        .withSubcommands(
            CommandAPICommand("player")
                .withSubcommands(
                    CommandAPICommand("proxy")
                        .withArguments(StringArgument("playerProxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                            redisController.getAllHashValues("rv-players-name")
                        }))
                        .withPermission("redivelocity.admin.player.proxy")
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
                            val playerName = args[0] as String
                            val playerProxy = redisController.getHashField("rv-players-proxy",
                                getUUID(playerName).toString()
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
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
                            val playerName = args[0] as String
                            val lastSeen = redisController.getHashField("rv-players-lastseen", getUUID(playerName).toString())
                            val isOnline: Boolean =
                                redisController.getHashField("rv-players-name", getUUID(playerName).toString())?.contains(playerName) == true
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
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
                            val playerName = args[0] as String
                            val playerIp = redisController.getHashField("rv-players-ip", getUUID(playerName).toString())
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
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
                            val playerName = args[0] as String
                            val playerUuid = getUUID(playerName).toString()
                            sender.sendMessage(miniMessage.deserialize("$prefix <gray>The player <aqua>$playerName</aqua> has the UUID: <aqua><hover:show_text:'<aqua>Click to copy</aqua>'><click:copy_to_clipboard:$playerUuid>$playerUuid</click></hover></gray>"))
                        }),
                ),
            CommandAPICommand("proxy")
                .withSubcommands(
                    CommandAPICommand("list")
                        .withPermission("redivelocity.admin.proxy.list")
                        .executesPlayer(PlayerCommandExecutor { sender, _ ->
                            val proxies = redisController.getList("rv-proxies")
                            val proxiesPrettyNames: MutableList<String> = mutableListOf()
                            proxies?.forEach { proxyId ->
                                proxiesPrettyNames.add("$prefix <aqua>$proxyId</aqua> <dark_grey>(<grey>Players: </grey><aqua>${redisController.getHashField("rv-proxy-players", proxyId)}</aqua>)</dark_grey>")
                            }
                            val proxiesPrettyString = proxiesPrettyNames.joinToString(separator = "<br>")
                            if (proxies != null) {
                                if (proxies.isEmpty()) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no connected proxies.</gray>"))
                                    return@PlayerCommandExecutor
                                } else {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently connected proxies:<br>$proxiesPrettyString</gray>"))
                                }
                            } else {
                                sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no connected proxies.</gray>"))
                                return@PlayerCommandExecutor
                            }
                        }),
                    CommandAPICommand("players")
                        .withOptionalArguments(StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                            redisController.getList("rv-proxies")
                        }))
                        .withPermission("redivelocity.admin.proxy.players")
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
                            val proxyId = args.getOptional(0).getOrNull() as? String
                            val players = redisController.getAllHashValues("rv-players-name")
                            val playersPrettyNames: MutableList<String> = mutableListOf()
                            players?.forEach { player ->
                                val playerProxy = redisController.getHashField("rv-players-proxy", proxy.getPlayer(player).orElse(null).uniqueId.toString())
                                if (proxyId == null) {
                                    playersPrettyNames.add("$prefix <aqua>$player</aqua> <dark_gray>(<aqua>$playerProxy</aqua>)</dark_gray>")
                                } else {
                                    if (playerProxy == proxyId) {
                                        playersPrettyNames.add("$prefix <aqua>$player</aqua>")
                                    }
                                }
                            }
                            val playersPrettyString = playersPrettyNames.joinToString(separator = "<br>")
                            if (players != null) {
                                if (players.isEmpty()) {
                                    sender.sendMessage(miniMessage.deserialize("$prefix <gray>There are currently no players online.</gray>"))
                                    return@PlayerCommandExecutor
                                } else {
                                    if (proxyId != null) {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently online players on proxy <aqua>$proxyId</aqua>:<br>$playersPrettyString</gray>"))
                                    } else {
                                        sender.sendMessage(miniMessage.deserialize("$prefix <gray>Currently online players:<br>$playersPrettyString</gray>"))

                                    }
                                }
                            }
                        }),
                    CommandAPICommand("playercount")
                        .withOptionalArguments(StringArgument("proxy").replaceSuggestions(ArgumentSuggestions.stringCollection {
                            redisController.getList("rv-proxies")
                        }))
                        .withPermission("redivelocity.admin.proxy.playercount")
                        .executesPlayer(PlayerCommandExecutor { sender, args ->
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
                        })
                )
        )
        .register(rediVelocity)
}