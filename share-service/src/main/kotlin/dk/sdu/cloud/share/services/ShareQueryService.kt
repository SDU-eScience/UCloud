package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.bearerAuth
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.withoutAuthentication
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.canonicalPath
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupFilesRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.share.api.MinimalShare
import dk.sdu.cloud.share.api.SharesByPath
import kotlinx.coroutines.runBlocking

class ShareQueryService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: ShareDAO<Session>,
    private val client: AuthenticatedClient
) {
    fun list(
        user: String,
        sharedByMe: Boolean,
        paging: NormalizedPaginationRequest = NormalizedPaginationRequest(null, null)
    ): Page<SharesByPath> {
        val page = db.withTransaction {
            dao.list(
                it,
                AuthRequirements(user, ShareRole.PARTICIPANT),
                ShareRelationQuery(user, sharedByMe),
                paging = paging
            )
        }

        return Page(
            page.groupCount,
            paging.itemsPerPage,
            paging.page,
            page.allSharesForPage.groupByPath(user)
        )
    }

    fun findSharesForPath(
        user: String,
        path: String,
        userAccessToken: String
    ): SharesByPath {
        val userCloud = client.withoutAuthentication().bearerAuth(userAccessToken)

        val stat =
            runBlocking {
                FileDescriptions.stat.call(
                    StatRequest(path),
                    userCloud
                ).orThrow()
            }

        return db.withTransaction { dao.findAllByPath(it, AuthRequirements(user, ShareRole.PARTICIPANT), stat.canonicalPath) }
            .groupByPath(user)
            .single()
    }

    suspend fun listFiles(
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<StorageFile> {
        val page = db.withTransaction { dao.listSharedToMe(it, user, paging) }
        val fileIds = page.items.map { it.fileId }

        val lookupResponse = LookupDescriptions.reverseLookupFiles.call(
            ReverseLookupFilesRequest(fileIds),
            client
        ).orThrow()

        return Page(
            page.itemsInTotal,
            page.itemsPerPage,
            page.pageNumber,
            lookupResponse.files.filterNotNull()
        )
    }

    private fun List<InternalShare>.groupByPath(user: String): List<SharesByPath> {
        val byPath = groupBy { it.path }
        return byPath.map { (path, sharesForPath) ->
            val owner = sharesForPath.first().owner
            val sharedByMe = owner == user

            SharesByPath(path, owner, sharedByMe, sharesForPath.map {
                MinimalShare(it.id, it.sharedWith, it.rights, it.state)
            })
        }
    }
}
