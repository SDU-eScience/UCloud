package dk.sdu.cloud.task.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.task.api.Task

interface TaskDao<Session> {
    fun findOrNull(session: Session, id: String, user: String): Task?
    fun create(
        session: Session,
        title: String,
        initialStatus: String?,
        owner: String,
        processor: SecurityPrincipal
    ): String

    fun updateStatus(session: Session, id: String, status: String, user: String): Boolean
    fun markAsComplete(session: Session, id: String, processor: SecurityPrincipal): Boolean
    fun list(session: Session, pagination: NormalizedPaginationRequest, user: SecurityPrincipal): Page<Task>
}
