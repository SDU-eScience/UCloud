package dk.sdu.cloud.service

import org.joda.time.DateTimeZone as JodaDateTimeZone
import java.time.ZoneId

val Time.javaTimeZone get() = ZoneId.of("Europe/Copenhagen")
