package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class DowntimeManagementService<Session>(
    private val db: DBSessionFactory<Session>,
    private val downtimeDAO: DowntimeDAO<Session>
) {
    fun add(user: SecurityPrincipal, downtime: DowntimeWithoutId) {
        db.withTransaction { session ->
            downtimeDAO.add(session, user, downtime)
        }
    }

    fun remove(user: SecurityPrincipal, id: Long) {
        db.withTransaction { session ->
            downtimeDAO.remove(session, user, id)
        }
    }

    fun removeExpired(user: SecurityPrincipal) {
        db.withTransaction { session ->
            downtimeDAO.removeExpired(session, user)
        }
    }

    fun listUpcoming(user: SecurityPrincipal, paging: NormalizedPaginationRequest): Page<Downtime> {
        return db.withTransaction { session ->
            downtimeDAO.listUpcoming(session, user, paging)
        }
    }

    fun listAll(user: SecurityPrincipal, paging: NormalizedPaginationRequest): Page<Downtime> {
        return db.withTransaction { session ->
            downtimeDAO.listAll(session, user, paging)
        }
    }

    fun getById(user: SecurityPrincipal, id: Long): Downtime {
        return db.withTransaction { session ->
            downtimeDAO.getById(session, user, id)
        }
    }
}