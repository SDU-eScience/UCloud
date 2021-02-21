package dk.sdu.cloud.service

import org.joda.time.DateTimeZone as JodaDateTimeZone
import java.time.ZoneId as JavaZoneId
import java.time.Instant as JavaInstant
import org.joda.time.Instant as JodaInstant

/**
 * Acts as a time source for all of UCloud
 *
 * Tests can mock the time provider by substituting the [provider]. The default provider is [SystemTimeProvider].
 *
 * @see StaticTimeProvider
 * @see SystemTimeProvider
 */
object Time : TimeProvider {
    var provider: TimeProvider = SystemTimeProvider
    val javaTimeZone = JavaZoneId.of("Europe/Copenhagen")
    val jodaTimeZone = JodaDateTimeZone.forID("Europe/Copenhagen")

    override fun now(): Long = provider.now()
}

interface TimeProvider {
    /**
     * Returns the current time expressed as milliseconds since
     * the time 00:00:00 UTC on January 1, 1970.
     *
     * @see System.currentTimeMillis
     */
    fun now(): Long
}

object SystemTimeProvider : TimeProvider {
    override fun now(): Long = System.currentTimeMillis()
}

object StaticTimeProvider : TimeProvider {
    var time: Long = 0L
    override fun now(): Long = time
}

fun epochToJoda(ts: Long): JodaInstant {
    return JodaInstant.ofEpochMilli(ts)
}

fun epochToJava(ts: Long): JavaInstant {
    return JavaInstant.ofEpochMilli(ts)
}
