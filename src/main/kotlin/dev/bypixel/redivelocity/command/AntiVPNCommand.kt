package dev.bypixel.redivelocity.command

import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.antivpn.AntiVPNManager
import dev.bypixel.redivelocity.antivpn.IpManager
import dev.dejvokep.boostedyaml.route.Route
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.anyExecutor
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.stringArgument
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage

@OptIn(ExperimentalLettuceCoroutinesApi::class)
object AntiVPNCommand {
    private val mm = MiniMessage.miniMessage()
    private val prefix = RediVelocity.instance.messageConfig.getString(Route.fromString("prefix"))

    init {
        commandTree("antivpn") {
            withPermission("redivelocity.antivpn.command")

            literalArgument("whitelist") {
                withPermission("redivelocity.antivpn.command.whitelist")

                literalArgument("add") {
                    withPermission("redivelocity.antivpn.command.whitelist.add")

                    literalArgument("ip") {
                        withPermission("redivelocity.antivpn.command.whitelist.add.ip")
                        stringArgument("ip") {
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val ip = (arguments[0] as String).trim()

                                    if (!IpManager.isValidIpV4OrV6(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid IP address."))
                                        return@launch
                                    }

                                    if (AntiVPNManager.isIpWhitelisted(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>IP <white>$ip <red>is already whitelisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.addIpToWhitelist(ip)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully added IP <color:#4bfb00>$ip <white>to the AntiVPN whitelist."))
                                }
                            }
                        }
                    }

                    literalArgument("asn") {
                        withPermission("redivelocity.antivpn.command.whitelist.add.asn")
                        stringArgument("asn") {
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val asn = (arguments[0] as String).trim()

                                    if (asn.isEmpty()) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid ASN."))
                                        return@launch
                                    }

                                    if (AntiVPNManager.isAsnWhitelisted(asn)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>ASN <white>$asn <red>is already whitelisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.addAsnToWhitelist(asn)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully added ASN <color:#4bfb00>$asn <white>to the AntiVPN whitelist."))
                                }
                            }
                        }
                    }
                }

                literalArgument("remove") {
                    withPermission("redivelocity.antivpn.command.whitelist.remove")

                    literalArgument("ip") {
                        withPermission("redivelocity.antivpn.command.whitelist.remove.ip")
                        stringArgument("ip") {
                            replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                                RediVelocityCoroutineScope.future(Dispatchers.IO) {
                                    AntiVPNManager.getAllWhitelistedIps()
                                }
                            })
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val ip = (arguments[0] as String).trim()

                                    if (!IpManager.isValidIpV4OrV6(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid IP address."))
                                        return@launch
                                    }

                                    if (!AntiVPNManager.isIpWhitelisted(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>IP <white>$ip <red>is not whitelisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.removeIpFromWhitelist(ip)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully removed IP <color:#4bfb00>$ip <white>from the AntiVPN whitelist."))
                                }
                            }
                        }
                    }

                    literalArgument("asn") {
                        withPermission("redivelocity.antivpn.command.whitelist.remove.asn")
                        stringArgument("asn") {
                            replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                                RediVelocityCoroutineScope.future(Dispatchers.IO) {
                                    AntiVPNManager.getAllWhitelistedAsns()
                                }
                            })
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val asn = (arguments[0] as String).trim()

                                    if (asn.isEmpty()) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid ASN."))
                                        return@launch
                                    }

                                    if (!AntiVPNManager.isAsnWhitelisted(asn)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>ASN <white>$asn <red>is not whitelisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.removeAsnFromWhitelist(asn)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully removed ASN <color:#4bfb00>$asn <white>from the AntiVPN whitelist."))
                                }
                            }
                        }
                    }
                }
            }

            literalArgument("blacklist") {
                withPermission("redivelocity.antivpn.command.blacklist")

                literalArgument("add") {
                    withPermission("redivelocity.antivpn.command.blacklist.add")

                    literalArgument("ip") {
                        withPermission("redivelocity.antivpn.command.blacklist.add.ip")
                        stringArgument("ip") {
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val ip = (arguments[0] as String).trim()

                                    if (!IpManager.isValidIpV4OrV6(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid IP address."))
                                        return@launch
                                    }

                                    if (AntiVPNManager.isIpBlacklisted(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>IP <white>$ip <red>is already blacklisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.addIpToBlacklist(ip)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully added IP <color:#4bfb00>$ip <white>to the AntiVPN blacklist."))
                                }
                            }
                        }
                    }

                    literalArgument("asn") {
                        withPermission("redivelocity.antivpn.command.blacklist.add.asn")
                        stringArgument("asn") {
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val asn = (arguments[0] as String).trim()

                                    if (asn.isEmpty()) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid ASN."))
                                        return@launch
                                    }

                                    if (AntiVPNManager.isAsnBlacklisted(asn)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>ASN <white>$asn <red>is already blacklisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.addAsnToBlacklist(asn)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully added ASN <color:#4bfb00>$asn <white>to the AntiVPN blacklist."))
                                }
                            }
                        }
                    }
                }

                literalArgument("remove") {
                    withPermission("redivelocity.antivpn.command.blacklist.remove")

                    literalArgument("ip") {
                        withPermission("redivelocity.antivpn.command.blacklist.remove.ip")
                        stringArgument("ip") {
                            replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                                RediVelocityCoroutineScope.future(Dispatchers.IO) {
                                    AntiVPNManager.getAllBlacklistedIps()
                                }
                            })
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val ip = (arguments[0] as String).trim()

                                    if (!IpManager.isValidIpV4OrV6(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid IP address."))
                                        return@launch
                                    }

                                    if (!AntiVPNManager.isIpBlacklisted(ip)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>IP <white>$ip <red>is not blacklisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.removeIpFromBlacklist(ip)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully removed IP <color:#4bfb00>$ip <white>from the AntiVPN blacklist."))
                                }
                            }
                        }
                    }

                    literalArgument("asn") {
                        withPermission("redivelocity.antivpn.command.blacklist.remove.asn")
                        stringArgument("asn") {
                            replaceSuggestions(ArgumentSuggestions.stringCollectionAsync {
                                RediVelocityCoroutineScope.future(Dispatchers.IO) {
                                    AntiVPNManager.getAllBlacklistedAsns()
                                }
                            })
                            anyExecutor { player, arguments ->
                                RediVelocityCoroutineScope.launch(Dispatchers.IO) {
                                    val asn = (arguments[0] as String).trim()

                                    if (asn.isEmpty()) {
                                        player.sendMessage(mm.deserialize("$prefix <red>Please provide a valid ASN."))
                                        return@launch
                                    }

                                    if (!AntiVPNManager.isAsnBlacklisted(asn)) {
                                        player.sendMessage(mm.deserialize("$prefix <red>ASN <white>$asn <red>is not blacklisted."))
                                        return@launch
                                    }

                                    AntiVPNManager.removeAsnFromBlacklist(asn)
                                    player.sendMessage(mm.deserialize("$prefix <white>Successfully removed ASN <color:#4bfb00>$asn <white>from the AntiVPN blacklist."))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}