package dk.sdu.cloud.app.fs.services

import dk.sdu.cloud.SecurityPrincipalToken
import dk.sdu.cloud.app.fs.api.AppFileSystems
import dk.sdu.cloud.app.fs.api.FileSystemCalls
import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.LINUX_FS_USER_UID
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import java.util.*

class SharedFileSystemService<DBSession>(
    private val backendService: BackendService,
    private val serviceClient: AuthenticatedClient,

    private val db: DBSessionFactory<DBSession>,
    private val fileSystemDao: FileSystemDao<DBSession>
) {
    suspend fun create(token: SecurityPrincipalToken, backend: String?, title: String): String {
        val allocatedId = UUID.randomUUID().toString()

        val resolvedBackend = backendService.verifyBackend(backend)

        db.withTransaction { fileSystemDao.create(it, allocatedId, resolvedBackend, token, title) }

        try {
            backendService.getBackend(backend).create.call(
                FileSystemCalls.Create.Request(allocatedId, LINUX_FS_USER_UID),
                serviceClient
            ).orThrow()
        } catch (ex: Throwable) {
            db.withTransaction { fileSystemDao.delete(it, allocatedId, resolvedBackend, token) }
            throw ex
        }

        db.withTransaction { fileSystemDao.markAsActive(it, allocatedId, resolvedBackend, token) }
        return allocatedId
    }

    suspend fun view(
        token: SecurityPrincipalToken,
        systemId: String,
        calculateSize: Boolean
    ): AppFileSystems.View.Response {
        val fs = db.withTransaction { fileSystemDao.view(it, systemId, token) }
        val backend = backendService.getBackend(fs.backend)
        val size = if (calculateSize) {
            backend.view.call(FileSystemCalls.View.Request(systemId), serviceClient).orThrow().size
        } else {
            0L
        }

        return AppFileSystems.View.Response(fs, size)
    }

    suspend fun calculateSize(fileSystem: SharedFileSystem): Long {
        val backend = backendService.getBackend(fileSystem.backend)
        return backend.view.call(FileSystemCalls.View.Request(fileSystem.id), serviceClient).orThrow().size
    }

    suspend fun list(token: SecurityPrincipalToken, pagination: NormalizedPaginationRequest): Page<SharedFileSystem> {
        return db.withTransaction { fileSystemDao.list(it, pagination, token) }
    }

    suspend fun delete(token: SecurityPrincipalToken, systemId: String) {
        val fs = db.withTransaction { fileSystemDao.view(it, systemId, token) }
        val backend = backendService.getBackend(fs.backend)

        db.withTransaction { fileSystemDao.markAsDeleting(it, systemId, backend.name, token) }

        try {
            backend.delete.call(FileSystemCalls.Delete.Request(systemId), serviceClient).orThrow()
        } catch (ex: Throwable) {
            // We return it to active. The delete call might already have caused damage to the file system however.
            db.withTransaction { fileSystemDao.markAsActive(it, systemId, backend.name, token) }
            throw ex
        }

        db.withTransaction { fileSystemDao.delete(it, systemId, backend.name, token) }

    }
}
