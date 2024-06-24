package de.bypixeltv.redivelocity.config

class Config {
    var redisHost: String = "127.0.0.1"
    var redisPort: Int = 6379
    var redisUsername: String = "default"
    var redisPassword: String = "password"
    var useSsl: Boolean = false
    var redisChannel: String = "redivelocity-players"
    var jsonFormat: Boolean = true
    var prefix = "<dark_gray>[<aqua>ℹ</aqua>]</dark_gray> <color:#0079FF>⌞RediVelocity⌝</color> <dark_gray>◘</dark_gray>"
    var kickMessage = "<dark_grey>- <dark_red>Sorry, but you are blocked</dark_red> -</dark_grey><br><br><grey>You got blocked from <aqua>Example.net</aqua>! You are not allowed to join this <aqua>Network</aqua>.<br>If you think this is a mistake, you can join our <blue>Discord Server</blue> to get help!</grey><br><br><blue><b><u>dc.example.net</u></b></blue>"
}