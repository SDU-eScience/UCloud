package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode

class DowntimeManagementService<Session>(
    private val db: DBSessionFactory<Session>,
    private val downtimeDAO: DowntimeDAO<Session>
) {
    fun add(user: SecurityPrincipal, downtime: DowntimeWithoutId) {
        verifyUserIsPrivileged(user)
        db.withTransaction { session ->
            downtimeDAO.add(session, downtime)
        }
    }

    fun remove(user: SecurityPrincipal, id: Long) {
        verifyUserIsPrivileged(user)
        db.withTransaction { session ->
            downtimeDAO.remove(session, id)
        }
    }

    fun removeExpired(user: SecurityPrincipal) {
        verifyUserIsPrivileged(user)
        db.withTransaction { session ->
            downtimeDAO.removeExpired(session, user)
        }
    }

    fun listUpcoming(paging: NormalizedPaginationRequest): Page<Downtime> {
        return db.withTransaction { session ->
            downtimeDAO.listUpcoming(session, paging)
        }
    }

    fun listAll(user: SecurityPrincipal, paging: NormalizedPaginationRequest): Page<Downtime> {
        verifyUserIsPrivileged(user)
        return db.withTransaction { session ->
            downtimeDAO.listAll(session, paging)
        }
    }

    fun getById(id: Long): Downtime {
        return db.withTransaction { session ->
            downtimeDAO.getById(session, id)
        }
    }
}

fun verifyUserIsPrivileged(user: SecurityPrincipal) {
    if (user.role !in Roles.PRIVILEDGED) throw RPCException("User is not privileged enough.", HttpStatusCode.Forbidden)
}