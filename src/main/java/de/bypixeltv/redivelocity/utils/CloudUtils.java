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

package de.bypixeltv.redivelocity.utils;

import de.vulpescloud.wrapper.Wrapper;
import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.wrapper.holder.ServiceInfoHolder;

public class CloudUtils {
    public static String getServiceName(String cloud) {
        if (cloud.equalsIgnoreCase("cloudnet")) {
            final ServiceInfoHolder serviceInfoHolder = InjectionLayer.ext().instance(ServiceInfoHolder.class);
            return serviceInfoHolder.serviceInfo().name();
        } else if (cloud.equalsIgnoreCase("vulpescloud")) {
            return Wrapper.instance.getServiceName();
        } else {
            return null;
        }
    }

}
