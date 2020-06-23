package dk.sdu.cloud.downtime.management.services

import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.downtime.management.api.Downtime
import dk.sdu.cloud.downtime.management.api.DowntimeWithoutId
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import io.ktor.http.HttpStatusCode

class DowntimeManagementService(
    private val db: DBContext,
    private val downtimeDao: DowntimeDao
) {
    suspend fun add(user: SecurityPrincipal, downtime: DowntimeWithoutId) {
        verifyUserIsPrivileged(user)
        downtimeDao.add(db, downtime)
    }

    suspend fun remove(user: SecurityPrincipal, id: Long) {
        verifyUserIsPrivileged(user)
        downtimeDao.remove(db, id)

    }

    suspend fun removeExpired(user: SecurityPrincipal) {
        verifyUserIsPrivileged(user)
        downtimeDao.removeExpired(db, user)
    }

    suspend fun listPending(paging: NormalizedPaginationRequest): Page<Downtime> {
        return downtimeDao.listPending(db, paging)

    }

    suspend fun listAll(user: SecurityPrincipal, paging: NormalizedPaginationRequest): Page<Downtime> {
        verifyUserIsPrivileged(user)
        return downtimeDao.listAll(db, paging)
    }

    suspend fun getById(id: Long): Downtime {
        return downtimeDao.getById(db, id)
    }
}

fun verifyUserIsPrivileged(user: SecurityPrincipal) {
    if (user.role !in Roles.PRIVILEGED) throw RPCException("User is not privileged enough.", HttpStatusCode.Forbidden)
}
