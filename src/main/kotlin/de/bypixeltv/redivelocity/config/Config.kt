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
}