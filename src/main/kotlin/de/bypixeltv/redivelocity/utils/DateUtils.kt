package de.bypixeltv.redivelocity.utils

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    fun Long.asDateString(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        return sdf.format(Date(this))
    }
}