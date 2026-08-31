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

package dev.bypixel.redivelocity.util

import app.simplecloud.controller.api.ControllerApi
import org.vulpesstudios.vulpescloud.bridge.BridgeAPI
import eu.cloudnetservice.driver.inject.InjectionLayer
import eu.cloudnetservice.wrapper.configuration.WrapperConfiguration

object CloudUtil {
    fun getServiceName(cloud: String): String {
        return when (cloud.lowercase()) {
            "simplecloud" -> {
                val controllerApi = ControllerApi.createFutureApi()
                val group =
                    controllerApi.getServers().getServerById(System.getenv("SIMPLECLOUD_UNIQUE_ID")).join().group
                val serverId =
                    controllerApi.getServers().getServerById(System.getenv("SIMPLECLOUD_UNIQUE_ID")).join().numericalId
                "$group-$serverId"
            }

            "vulpescloud" -> {
                val service = BridgeAPI.createFutureAPI().getServicesAPI().getLocalService().join()
                    ?: throw IllegalStateException("VulpesCloud service is null! Please report this to the VulpesCloud team. https://github.com/VulpesCloud/VulpesCloud/issues")
                service.name()
            }

            "cloudnet" -> {
                val wrapperConfiguration = InjectionLayer.ext().instance(WrapperConfiguration::class.java)

                return wrapperConfiguration.serviceInfoSnapshot().name()
            }

            else -> {
                throw IllegalArgumentException("Unsupported cloud system: $cloud")
            }
        }
    }
}