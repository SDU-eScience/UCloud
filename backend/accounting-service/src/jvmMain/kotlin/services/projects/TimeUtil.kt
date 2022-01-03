package dk.sdu.cloud.accounting.services.projects

import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

fun LocalDateTime.toTimestamp(): Long = toDateTime(DateTimeZone.UTC).millis
