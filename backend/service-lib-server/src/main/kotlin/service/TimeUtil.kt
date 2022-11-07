package dk.sdu.cloud.service

import org.joda.time.DateTimeZone as JodaDateTimeZone
import org.joda.time.LocalDateTime as JodaLocalDateTime
import java.time.LocalDateTime as JavaLocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

val Time.javaTimeZone get() = ZoneId.of("Europe/Copenhagen")

fun JavaLocalDateTime.toTimestamp(): Long {
    return toInstant(ZoneOffset.UTC).toEpochMilli()
}

fun timestampToLocalDateTime(ts: Long): JavaLocalDateTime {
    return JavaLocalDateTime.ofInstant(Date(ts).toInstant(), ZoneId.from(ZoneOffset.UTC))
}
