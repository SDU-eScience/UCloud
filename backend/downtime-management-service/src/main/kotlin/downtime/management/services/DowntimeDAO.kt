package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import java.util.*
import javax.persistence.*

object DowntimeTable : SQLTable("downtimes") {
    val start = timestamp("start_time", notNull = true)
    val end = timestamp("end_time", notNull = true)
    val text = text("text", notNull = true)
    val id = long("id")
}

interface DowntimeDAO {
    suspend fun add(
        db: DBContext,
        downtime: DowntimeWithoutId
    )

    suspend fun remove(
        db: DBContext,
        id: Long
    )

    suspend fun listAll(
        db: DBContext,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    suspend fun listPending(
        db: DBContext,
        paging: NormalizedPaginationRequest
    ): Page<Downtime>

    suspend fun removeExpired(
        db: DBContext,
        user: SecurityPrincipal
    )

    suspend fun getById(
        db: DBContext,
        id: Long
    ): Downtime
}
