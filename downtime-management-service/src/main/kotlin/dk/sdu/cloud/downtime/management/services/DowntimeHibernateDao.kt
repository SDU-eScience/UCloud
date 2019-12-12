package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import java.util.*

class DowntimeHibernateDao : DowntimeDAO<HibernateSession> {
    override fun add(session: HibernateSession, user: SecurityPrincipal, downtime: DowntimeWithoutId) {
        val entity = DowntimeEntity(downtime.start, downtime.end, downtime.text)
        session.save(entity)
    }

    override fun remove(session: HibernateSession, user: SecurityPrincipal, id: Long) {
        session.deleteCriteria<DowntimeEntity> {
            entity[DowntimeEntity::id] equal id
        }.executeUpdate()
    }

    override fun removeExpired(session: HibernateSession, user: SecurityPrincipal) {
        val now = Date().time
        session.deleteCriteria<DowntimeEntity> {
            entity[DowntimeEntity::end] lessThan now
        }.executeUpdate()
    }

    override fun listAll(
        session: HibernateSession,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<Downtime> =
        session.paginatedCriteria<DowntimeEntity>(paging) {
            literal(true).toPredicate()
        }.mapItems {
            Downtime(it.id!!, it.start, it.end, it.text)
        }


    override fun listUpcoming(
        session: HibernateSession,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<Downtime> {
        val now = Date().time
        return session.paginatedCriteria<DowntimeEntity>(paging) {
            entity[DowntimeEntity::start] greaterThan now
        }.mapItems {
            Downtime(it.id!!, it.start, it.end, it.text)
        }
    }

    override fun getById(session: HibernateSession, user: SecurityPrincipal, id: Long): Downtime {
        val entity = DowntimeEntity[session, id] ?: throw RPCException("No downtime with id found", HttpStatusCode.NotFound)
        return Downtime(entity.id!!, entity.start, entity.end, entity.text)
    }
}