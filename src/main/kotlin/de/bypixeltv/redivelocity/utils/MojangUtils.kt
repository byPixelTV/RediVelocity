package de.bypixeltv.redivelocity.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.velocitypowered.api.util.UuidUtils
import de.bypixeltv.redivelocity.RediVelocity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.*
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class MojangUtils @Inject constructor(private val rediVelocity: RediVelocity) {

    fun getUUID(username: String): UUID? {
        try {
            val url = URI("https://api.mojang.com/users/profiles/minecraft/$username")
            val reader = BufferedReader(InputStreamReader(url.toURL().openStream()))
            val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
            val uuid = jsonObject["id"].asString
            return UuidUtils.fromUndashed(uuid)
        } catch (e: IOException) {
            rediVelocity.sendErrorLogs("Failed to get UUID for $username!")
        }
        return null
    }

    fun getName(uuid: UUID): String? {
        try {
            val url = URI("https://api.mojang.com/user/profile/${UuidUtils.toUndashed(uuid)}")
            val reader = BufferedReader(InputStreamReader(url.toURL().openStream()))
            val jsonObject = Gson().fromJson(reader, JsonObject::class.java)
            return jsonObject["name"].asString
        } catch (e: IOException) {
            rediVelocity.sendErrorLogs("Failed to get Name for $uuid!")
        }
        return null
    }

    fun isValidUUID(uuidString: String): Boolean {
        return try {
            UUID.fromString(uuidString)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}