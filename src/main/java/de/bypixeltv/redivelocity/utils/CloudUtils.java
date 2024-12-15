package de.bypixeltv.redivelocity.utils;

import de.vulpescloud.wrapper.Wrapper;
import eu.cloudnetservice.driver.inject.InjectionLayer;
import eu.cloudnetservice.wrapper.holder.ServiceInfoHolder;
import eu.thesimplecloud.plugin.startup.CloudPlugin;

public class CloudUtils {
    public static String getServiceName(String cloud) {
        if (cloud.equalsIgnoreCase("simplecloud")) {
            return CloudPlugin.getInstance().thisService().getName();
        } else if (cloud.equalsIgnoreCase("cloudnet")) {
            final ServiceInfoHolder serviceInfoHolder = InjectionLayer.ext().instance(ServiceInfoHolder.class);
            return serviceInfoHolder.serviceInfo().name();
        } else if (cloud.equalsIgnoreCase("vulpescloud")) {
            return Wrapper.instance.getService().getName();
        } else {
            return null;
        }
    }

}
