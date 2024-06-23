package de.bypixeltv.redivelocity.config

class Config {
    var redisHost: String = "127.0.0.1"
    var redisPort: Int = 6379
    var redisUsername: String = "default"
    var redisPassword: String = "password"
    var useSsl: Boolean = false
    var redisChannel: String = "redivelocity-players"
    var jsonFormat: Boolean = true
}