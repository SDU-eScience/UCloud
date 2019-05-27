package dk.sdu.cloud.share.services

import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.share.api.MinimalShare
import dk.sdu.cloud.share.api.SharesByPath

class ShareQueryService<Session>(
    private val db: DBSessionFactory<Session>,
    private val dao: ShareDAO<Session>
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
        path: String
    ): SharesByPath {
        return db.withTransaction { dao.findAllByPath(it, AuthRequirements(user), path) }
            .groupByPath(user)
            .single()
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
