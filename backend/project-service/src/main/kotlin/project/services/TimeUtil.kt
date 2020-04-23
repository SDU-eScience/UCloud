package dk.sdu.cloud.project.services

import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime

fun LocalDateTime.toTimestamp(): Long = toDateTime(DateTimeZone.UTC).millis
