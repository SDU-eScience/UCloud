package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import io.ktor.http.HttpStatusCode
import io.ktor.util.toZonedDateTime
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

class DowntimeHibernateDao : DowntimeDAO {
    override suspend fun add(db: DBContext, downtime: DowntimeWithoutId) {
        if (downtime.start > downtime.end) {
            throw RPCException("Downtime can't end before it begins.", HttpStatusCode.BadRequest)
        }
        db.withSession { session ->
            val id = session.allocateId()
            session.insert(DowntimeTable) {
                set(DowntimeTable.start, LocalDateTime(downtime.start, DateTimeZone.UTC))
                set(DowntimeTable.end, LocalDateTime(downtime.end, DateTimeZone.UTC))
                set(DowntimeTable.text, downtime.text)
                set(DowntimeTable.id, id)
            }
        }
    }

    override suspend fun remove(db: DBContext, id: Long) {
        val count = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        DELETE FROM downtimes
                        WHERE id = ?id
                    """.trimIndent()
                ).rowsAffected
        }
        if (count == 0L) {
            throw RPCException("No downtime found by id.", HttpStatusCode.BadRequest)
        }
    }

    override suspend fun removeExpired(db: DBContext, user: SecurityPrincipal) {
        val now = Date().time
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("time", now / 1000)
                    },
                    """
                        DELETE FROM downtimes
                        WHERE end_time < to_timestamp(?time)
                    """.trimIndent()
                )
        }
    }

    override suspend fun listAll(db: DBContext, paging: NormalizedPaginationRequest): Page<Downtime> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    """
                        SELECT *
                        FROM downtimes
                        ORDER BY start_time ASC
                    """.trimIndent()
                ).rows
                .paginate(paging)
                .mapItems {
                    Downtime(
                        it.getField(DowntimeTable.id),
                        it.getField(DowntimeTable.start).toDateTime(DateTimeZone.UTC).millis,
                        it.getField(DowntimeTable.end).toDateTime(DateTimeZone.UTC).millis,
                        it.getField(DowntimeTable.text)
                    )
                }
        }
    }


    override suspend fun listPending(db: DBContext, paging: NormalizedPaginationRequest): Page<Downtime> {
        val now = Date().time
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("time", now / 1000)
                    },
                    """
                        SELECT *
                        FROM downtimes
                        WHERE end_time > to_timestamp(?time)
                        ORDER BY start_time
                    """.trimIndent()
                ).rows
                .paginate(paging)
                .mapItems {
                    Downtime(
                        it.getField(DowntimeTable.id),
                        it.getField(DowntimeTable.start).toDateTime(DateTimeZone.UTC).millis,
                        it.getField(DowntimeTable.end).toDateTime(DateTimeZone.UTC).millis,
                        it.getField(DowntimeTable.text)
                    )
                }
        }
    }

    override suspend fun getById(db: DBContext, id: Long): Downtime {
        val queryResult = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM downtimes
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.firstOrNull() ?: throw RPCException("No downtime with id found", HttpStatusCode.NotFound)
        }

        return Downtime(
            queryResult.getField(DowntimeTable.id),
            queryResult.getField(DowntimeTable.start).toDateTime(DateTimeZone.UTC).millis,
            queryResult.getField(DowntimeTable.end).toDateTime(DateTimeZone.UTC).millis,
            queryResult.getField(DowntimeTable.text)
        )
    }
}
