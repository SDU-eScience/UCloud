package dk.sdu.cloud.app.fs.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.*
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import org.hibernate.annotations.NaturalId
import java.util.*
import javax.persistence.*

@Entity
@Table(name = "file_systems")
data class FileSystemEntity(
    @get:Id
    @get:NaturalId
    var id: String,

    var backend: String,

    var owner: String,

    @Enumerated(EnumType.STRING)
    var state: FileSystemState,

    var title: String? = null,

    override var createdAt: Date = Date(System.currentTimeMillis()),

    override var modifiedAt: Date = Date(System.currentTimeMillis())
) : WithTimestamps {
    companion object : WithId<String>, HibernateEntity<FileSystemEntity>
}

class FileSystemHibernateDao : FileSystemDao<HibernateSession> {
    override fun create(
        session: HibernateSession,
        systemId: String,
        backend: String,
        owner: SecurityPrincipalToken,
        title: String?
    ) {
        val entity = FileSystemEntity(systemId, backend, owner.principal.username, FileSystemState.CREATING, title)
        session.save(entity)
    }

    override fun markAsActive(
        session: HibernateSession,
        systemId: String,
        backend: String,
        owner: SecurityPrincipalToken?
    ) {
        val existingEntity = findEntity(session, owner, backend, systemId)

        if (existingEntity.state !in setOf(FileSystemState.CREATING, FileSystemState.DELETING)) {
            throw RPCException("Cannot mark as active", HttpStatusCode.InternalServerError)
        }

        // We technically have a race-condition here if two instances for some reason are trying to call this.
        // But two different instances shouldn't be attempting this in the first place.
        existingEntity.state = FileSystemState.ACTIVE

        session.update(existingEntity)
    }

    override fun markAsDeleting(
        session: HibernateSession,
        systemId: String,
        backend: String,
        owner: SecurityPrincipalToken?
    ) {
        val existingEntity = findEntity(session, owner, backend, systemId)

        if (existingEntity.state == FileSystemState.DELETING) {
            throw RPCException("Cannot mark as deleting", HttpStatusCode.InternalServerError)
        }

        // We technically have a race-condition here if two instances for some reason are trying to call this.
        // But two different instances shouldn't be attempting this in the first place.
        existingEntity.state = FileSystemState.DELETING

        session.update(existingEntity)
    }

    override fun delete(session: HibernateSession, systemId: String, backend: String, owner: SecurityPrincipalToken?) {
        val existingEntity = findEntity(session, owner, backend, systemId)

        if (existingEntity.state != FileSystemState.DELETING) {
            throw RPCException("You should first mark the filesystem as deleting", HttpStatusCode.InternalServerError)
        }

        session.delete(existingEntity)
    }

    override fun view(session: HibernateSession, systemId: String, owner: SecurityPrincipalToken?): SharedFileSystem {
        val existingEntity = session.criteria<FileSystemEntity> {
            val isOwner =
                if (owner == null) literal(true).toPredicate()
                else (entity[FileSystemEntity::owner] equal owner.principal.username)

            (entity[FileSystemEntity::id] equal systemId) and (isOwner)
        }.uniqueResult() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        return SharedFileSystem(
            systemId,
            existingEntity.owner,
            existingEntity.backend,
            existingEntity.title ?: existingEntity.id,
            existingEntity.createdAt.time
        )
    }

    override fun list(
        session: HibernateSession,
        paginationRequest: NormalizedPaginationRequest,
        owner: SecurityPrincipalToken?
    ): Page<SharedFileSystem> {
        return session.paginatedCriteria<FileSystemEntity>(paginationRequest) {
            val isOwner =
                if (owner == null) literal(true).toPredicate()
                else (entity[FileSystemEntity::owner] equal owner.principal.username)

            isOwner
        }.mapItems {
            SharedFileSystem(it.id, it.owner, it.backend, it.title ?: it.id, it.createdAt.time)
        }
    }

    private fun findEntity(
        session: HibernateSession,
        owner: SecurityPrincipalToken?,
        backend: String,
        systemId: String
    ): FileSystemEntity {
        return session.criteria<FileSystemEntity> {
            val isOwner =
                if (owner == null) literal(true).toPredicate()
                else (entity[FileSystemEntity::owner] equal owner.principal.username)

            (entity[FileSystemEntity::backend] equal backend) and
                    (entity[FileSystemEntity::id] equal systemId) and
                    (isOwner)
        }.uniqueResult() ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
    }
}
