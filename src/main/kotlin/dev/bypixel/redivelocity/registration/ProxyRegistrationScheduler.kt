package dev.bypixel.redivelocity.registration

import dev.bypixel.redivelocity.heartbeat.HeartbeatScheduler

/** Registration is repaired atomically by the owner when writing its heartbeat. */
object ProxyRegistrationScheduler {
    val job get() = HeartbeatScheduler.job
}
