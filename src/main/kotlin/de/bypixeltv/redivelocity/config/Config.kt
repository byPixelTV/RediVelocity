package de.bypixeltv.redivelocity.config

class Config {
    var redisHost: String = "127.0.0.1"
    var redisPort: Int = 6379
    var redisUsername: String = "default"
    var redisPassword: String = "password"
    var redisChannel: String = "redivelocity-players"
    var jsonFormat: Boolean = true
}