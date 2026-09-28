/*
 * Copyright (c) 2024-present byPixelTV & contributors.
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

package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocity
import dev.bypixel.redivelocity.util.RediVelocityLogger
import org.json.JSONObject

object LeaderElectionListener : RedisListener("redivelocity:leader-election") {
    override fun onMessage(message: String) {
        val data = JSONObject(message)
        if (data.optString("recipient") != RediVelocity.instance.proxyId) return
        when (data.optString("action")) {
            "SET" -> RediVelocityLogger.info("This proxy has been elected as the leader proxy.")
            "REMOVED" -> RediVelocityLogger.info("This proxy is no longer the leader proxy.")
        }
    }
}
