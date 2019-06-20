package dk.sdu.cloud.app.fs.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page

enum class FileSystemState {
    CREATING,
    ACTIVE,
    DELETING
}

interface FileSystemDao<Session> {
    fun create(session: Session, systemId: String, backend: String, owner: SecurityPrincipalToken)
    fun markAsActive(session: Session, systemId: String, backend: String, owner: SecurityPrincipalToken?)
    fun markAsDeleting(session: Session, systemId: String, backend: String, owner: SecurityPrincipalToken?)
    fun delete(session: Session, systemId: String, backend: String, owner: SecurityPrincipalToken?)
    fun view(session: Session, systemId: String, owner: SecurityPrincipalToken?): SharedFileSystem
    fun list(
        session: Session,
        paginationRequest: NormalizedPaginationRequest,
        owner: SecurityPrincipalToken?
    ): Page<SharedFileSystem>
}
