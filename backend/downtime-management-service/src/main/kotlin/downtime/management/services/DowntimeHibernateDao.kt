package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedCriteria
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.util.*

class DowntimeHibernateDao : DowntimeDAO<HibernateSession> {
    override fun add(session: HibernateSession, downtime: DowntimeWithoutId) {
        if (downtime.start > downtime.end) {
            throw RPCException("Downtime can't end before it begins.", HttpStatusCode.BadRequest)
        }
        val entity = DowntimeEntity(Date(downtime.start), Date(downtime.end), downtime.text)
        session.save(entity)
    }

    override fun remove(session: HibernateSession, id: Long) {
        val count = session.deleteCriteria<DowntimeEntity> {
            entity[DowntimeEntity::id] equal id
        }.executeUpdate()
        if (count == 0) {
            throw RPCException("No downtime found by id.", HttpStatusCode.BadRequest)
        }
    }

    override fun removeExpired(session: HibernateSession, user: SecurityPrincipal) {
        val now = Date()
        session.deleteCriteria<DowntimeEntity> {
            entity[DowntimeEntity::end] lessThan now
        }.executeUpdate()
    }

    override fun listAll(session: HibernateSession, paging: NormalizedPaginationRequest): Page<Downtime> =
        session.paginatedCriteria<DowntimeEntity>(
            paging,
            orderBy = { listOf(ascending(entity[DowntimeEntity::start])) }
        ) {
            literal(true).toPredicate()
        }.mapItems {
            Downtime(it.id!!, it.start.time, it.end.time, it.text)
        }


    override fun listPending(session: HibernateSession, paging: NormalizedPaginationRequest): Page<Downtime> {
        val now = Date()
        return session.paginatedCriteria<DowntimeEntity>(
            paging,
            orderBy = { listOf(ascending(entity[DowntimeEntity::start]))}
        ) {
            entity[DowntimeEntity::end] greaterThan now
        }.mapItems {
            Downtime(it.id!!, it.start.time, it.end.time, it.text)
        }
    }

    override fun getById(session: HibernateSession, id: Long): Downtime {
        val entity =
            DowntimeEntity[session, id] ?: throw RPCException("No downtime with id found", HttpStatusCode.NotFound)
        return Downtime(entity.id!!, entity.start.time, entity.end.time, entity.text)
    }
}