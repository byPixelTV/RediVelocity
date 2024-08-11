package de.bypixeltv.redivelocity.utils

import eu.cloudnetservice.driver.inject.InjectionLayer
import eu.cloudnetservice.wrapper.holder.ServiceInfoHolder
import jakarta.inject.Singleton

@Singleton
class CloudNetUtils {

    private val cnServiceInfoHolder: ServiceInfoHolder = InjectionLayer.ext().instance(ServiceInfoHolder::class.java)

    fun getServicename(): String {
        return cnServiceInfoHolder.serviceInfo().name()
    }
}