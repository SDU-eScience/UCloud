package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.allocateId
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import io.ktor.http.HttpStatusCode
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*

object DowntimeTable : SQLTable("downtimes") {
    val start = timestamp("start_time", notNull = true)
    val end = timestamp("end_time", notNull = true)
    val text = text("text", notNull = true)
    val id = long("id")
}

class DowntimeDao {
    suspend fun add(db: DBContext, downtime: DowntimeWithoutId) {
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

    suspend fun remove(db: DBContext, id: Long) {
        val count = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        DELETE FROM downtimes
                        WHERE id = :id
                    """
                ).rowsAffected
        }
        if (count == 0L) {
            throw RPCException("No downtime found by id.", HttpStatusCode.BadRequest)
        }
    }

    suspend fun removeExpired(db: DBContext, user: SecurityPrincipal) {
        val now = Time.now()
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("time", now / 1000)
                    },
                    """
                        DELETE FROM downtimes
                        WHERE end_time < to_timestamp(:time)
                    """
                )
        }
    }

    suspend fun listAll(db: DBContext, paging: NormalizedPaginationRequest): Page<Downtime> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    """
                        SELECT *
                        FROM downtimes
                        ORDER BY start_time ASC
                    """
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


    suspend fun listPending(db: DBContext, paging: NormalizedPaginationRequest): Page<Downtime> {
        val now = Time.now()
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("time", now / 1000)
                    },
                    """
                        SELECT *
                        FROM downtimes
                        WHERE end_time > to_timestamp(:time)
                        ORDER BY start_time
                    """
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

    suspend fun getById(db: DBContext, id: Long): Downtime {
        val queryResult = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM downtimes
                        WHERE id = :id
                    """
                ).rows
                .firstOrNull() ?: throw RPCException("No downtime with id found", HttpStatusCode.NotFound)
        }

        return Downtime(
            queryResult.getField(DowntimeTable.id),
            queryResult.getField(DowntimeTable.start).toDateTime(DateTimeZone.UTC).millis,
            queryResult.getField(DowntimeTable.end).toDateTime(DateTimeZone.UTC).millis,
            queryResult.getField(DowntimeTable.text)
        )
    }
}
