package de.bypixeltv.redivelocity.config

class Config {
    var configVersion: Int = 4

    var redis: RedisConfig = RedisConfig()
    var cloudnet: CloudNetConfig = CloudNetConfig()
    var versionControl: VersionControlConfig = VersionControlConfig()
    var messages: MessagesConfig = MessagesConfig()
    var resourcepack: ResourcePackConfig = ResourcePackConfig()

    var jsonFormat: Boolean = true
    var playerCountSync: Boolean = true

    class RedisConfig {
        var host: String = "127.0.0.1"
        var port: Int = 6379
        var username: String = "default"
        var password: String = "password"
        var useSsl: Boolean = false
        var channel: String = "redivelocity-players"
    }

    class CloudNetConfig {
        var enabled: Boolean = false
        var cloudnetUseServiceId: Boolean = true
    }

    class VersionControlConfig {
        var enabled: Boolean = false
        var protocolVersion: Int = 754
        var kickMessage: String = "<dark_grey>- <dark_red>Version</dark_red> -</dark_grey><br><br><grey>You have to use the <aqua>Version</aqua> <blue>1.16.5</blue> to play on <aqua>Example.net</aqua>.<br>Please update your <aqua>Version</aqua> to join the <aqua>Server</aqua>!</grey>"
    }

    class MessagesConfig {
        var prefix: String = "<dark_gray>[<aqua>ℹ</aqua>]</dark_gray> <color:#0079FF>⌞RediVelocity⌝</color> <dark_gray>◘</dark_gray>"
        var kickMessage: String = "<dark_grey>- <dark_red>Sorry, but you are blocked</dark_red> -</dark_grey><br><br><grey>You got blocked from <aqua>Example.net</aqua>! You are not allowed to join this <aqua>Network</aqua>.<br>If you think this is a mistake, you can join our <blue>Discord Server</blue> to get help!</grey><br><br><blue><b><u>dc.example.net</u></b></blue>"
    }

    class ResourcePackConfig {
        var enabled: Boolean = false
        var forceResourcepack: Boolean = false
        var resourcepackUrl: String = "https://example.net/resourcepack.zip"
        var resourcepackMessage: String = "<dark_grey>- <dark_red>Resourcepack</dark_red> -</dark_grey><br><br><grey>You have to download the <aqua>Resourcepack</aqua> to play on <aqua>Example.net</aqua>.<br>Please click on accept to download the <aqua>Resourcepack</aqua> and join the <aqua>Server</aqua>!</grey>"
        var resourcepackKickMessage: String = "<dark_grey>- <dark_red>Resourcepack</dark_red> -</dark_grey><br><br><grey>You have to download the <aqua>Resourcepack</aqua> to play on <aqua>Example.net</aqua>.<brPlease click on accept to download the <aqua>Resourcepack</aqua> and join the <aqua>Server</aqua>!</grey>"
    }
}