package de.bypixeltv.redivelocity.utils

import jakarta.inject.Singleton
import java.text.SimpleDateFormat
import java.util.*

@Singleton
class DateUtils

fun Long.asDateString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    return sdf.format(Date(this))
}