package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp

object DowntimeTable : SQLTable("downtimes") {
    val start = timestamp("start_time", notNull = true)
    val end = timestamp("end_time", notNull = true)
    val text = text("text", notNull = true)
    val id = long("id")
}

