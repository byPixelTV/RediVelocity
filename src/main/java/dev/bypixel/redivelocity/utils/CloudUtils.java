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

package dev.bypixel.redivelocity.utils;

import app.simplecloud.controller.api.ControllerApi;
import de.vulpescloud.bridge.VulpesBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudUtils {
    private static final ControllerApi.Future controllerApi = ControllerApi.createFutureApi();

    public static @Nullable String getServiceName(@NotNull String cloud) {
        if (cloud.equalsIgnoreCase("simplecloud")) {
            String group = controllerApi.getServers().getServerById(System.getenv("SIMPLECLOUD_UNIQUE_ID")).join().getGroup();
            int serverId = controllerApi.getServers().getServerById(System.getenv("SIMPLECLOUD_UNIQUE_ID")).join().getNumericalId();
            return group + "-" + serverId;
        } else if (cloud.equalsIgnoreCase("vulpescloud")) {
            return VulpesBridge.INSTANCE.getServiceProvider().getLocalService().getName();
        } else {
            return null;
        }
    }
}
