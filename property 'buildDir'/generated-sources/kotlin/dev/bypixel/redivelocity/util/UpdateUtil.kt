/*
 * Copyright (c) 2025.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.bypixel.redivelocity.util

import dev.bypixel.redivelocity.RediVelocity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject

object UpdateUtil {
    suspend fun checkForUpdate() : Boolean {
        RediVelocityLogger.info("Checking for updates...")
        val latestVersionString = getLatestVersion()
        val currentVersionString = RediVelocity.server.pluginManager.getPlugin("redivelocity").get().description.version.orElse("0.0.0")
        val latestVersion = Version.fromString(latestVersionString)
        val currentVersion = Version.fromString(currentVersionString)
        val compare = latestVersion.compareTo(currentVersion)

        if (currentVersionString.contains("+")) {
            RediVelocityLogger.consoleMessage("<yellow>This is a <color:#ff0000><b>BETA build,</b></color> things may not work as expected, please report any bugs on <aqua>GitHub</aqua></yellow><br><aqua><b>https://github.com/byPixelTV/RediVelocity/issues</b></aqua>")
            return false
        }

        return if (compare == 0) {
            RediVelocityLogger.success("<green>The plugin is up to date! (v$currentVersionString)</green>")
            false
        } else if (compare < 0) {
            RediVelocityLogger.success("<yellow>You are running a newer version ($currentVersionString) than the latest release (v$latestVersionString).</yellow>")
            false
        } else {
            RediVelocityLogger.consoleMessage("<red>The plugin is not up to date!</red>")
            RediVelocityLogger.consoleMessage(" - Current version: <red>v currentVersionString</red>")
            RediVelocityLogger.consoleMessage(" - Available update: <green>v version</green>")
            RediVelocityLogger.consoleMessage(" - Download available at: <aqua>https://github.com/byPixelTV/RediVelocity/releases</aqua>")
            true
        }
    }

    suspend fun isUpdateAvailable(): Boolean {
        val latestVersionString = getLatestVersion()
        val currentVersionString = RediVelocity.server.pluginManager.getPlugin("redivelocity").get().description.version.orElse("0.0.0")
        val latestVersion = Version.fromString(latestVersionString)
        val currentVersion = Version.fromString(currentVersionString)
        val compare = latestVersion.compareTo(currentVersion)

        return compare > 0
    }

    suspend fun getLatestVersion(): String = withContext(Dispatchers.IO) {
        val okhttpClient = OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://api.github.com/repos/byPixelTV/RediVelocity/releases/latest")
            .build()

        okhttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unexpected code $response")
            }

            val responseBody = response.body.string()

            val jBody = JSONObject(responseBody)
            val tag = jBody.getString("tag_name").removePrefix("v")
            return@withContext tag
        }
    }
}