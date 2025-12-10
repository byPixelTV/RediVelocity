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

package dev.bypixel.redivelocity.pubsub

import dev.bypixel.lettucewrapper.listener.RedisListener
import dev.bypixel.redivelocity.RediVelocityCoroutineScope
import dev.bypixel.redivelocity.feature.globalPlayercount.PlayercountUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object PlayercountListener : RedisListener("redivelocity:global-player-updates") {
    override fun onMessage(message: String) {
        RediVelocityCoroutineScope.launch(Dispatchers.IO) {
            val jMsg = JSONObject(message)

            if (jMsg.has("action")) {
                when (jMsg.getString("action")) {
                    "UPDATE" -> {
                        PlayercountUtil.calcGlobalPlayercount()
                    }
                }
            }
        }
    }
}