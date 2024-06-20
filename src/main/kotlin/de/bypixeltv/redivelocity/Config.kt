package de.bypixeltv.redivelocity

class Config {
    var redisHost: String? = "127.0.0.1"
    var redisPort: Int = 6379
    var redisUsername: String? = "default"
    var redisPassword: String? = "password"
    var redisChannel: String? = "redivelocity-players"
    var messageFormat: String? = "{username};{uuid};{ip};{clientbrand};{timestamp}"
    var jsonFormat: Boolean = false
}