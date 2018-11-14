package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.asEnumSet
import dk.sdu.cloud.service.asInt
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.countWithPredicate
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.share.api.Share
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.SharesByPath
import dk.sdu.cloud.share.api.minimalize
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType
import javax.persistence.UniqueConstraint
import javax.persistence.criteria.Predicate

private const val COL_SHARED_WITH = "shared_with"
private const val COL_PATH = "path"

@Entity
@Table(
    name = "shares",
    uniqueConstraints = [
        UniqueConstraint(columnNames = [COL_SHARED_WITH, COL_PATH])
    ]
)
data class ShareEntity(
    @Id
    @GeneratedValue
    var id: Long = 0,

    var owner: String,

    @Column(name = COL_SHARED_WITH)
    var sharedWith: String,

    @Column(name = COL_PATH)
    var path: String,

    var rights: Int,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date,

    @Enumerated(EnumType.ORDINAL)
    var state: ShareState,

    var filename: String
) {
    companion object : HibernateEntity<ShareEntity>, WithId<Long>
}

class ShareHibernateDAO : ShareDAO<HibernateSession> {
    override fun find(
        session: HibernateSession,
        user: String,
        shareId: Long
    ): Share {
        return shareById(session, user, shareId, requireOwnership = false).toModel()
    }

    override fun list(
        session: HibernateSession,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<SharesByPath> {
        // Query number of paths and retrieve for current page
        val itemsInTotal = getNumberOfSharesStatusFilterPossible(session, user, false)

        val items = getSharesStatusFilterPossible(session, user, false, paging)

        return Page(
            itemsInTotal.toInt(),
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    override fun listByStatus(
        session: HibernateSession,
        user: String,
        status: ShareState,
        paging: NormalizedPaginationRequest
    ): Page<SharesByPath> {
        val count = getNumberOfSharesStatusFilterPossible(session, user, true, status)

        val items = getSharesStatusFilterPossible(session, user, true, paging, status)

        return Page(
            count.toInt(),
            paging.itemsPerPage,
            paging.page,
            items
        )
    }

    private fun getNumberOfSharesStatusFilterPossible(
        session: HibernateSession,
        user: String,
        statusSearch: Boolean,
        status: ShareState? = ShareState.ACCEPTED
    ): Long {
        return session.countWithPredicate<ShareEntity>(
            distinct = true,
            selection = { entity[ShareEntity::path] },
            predicate = {
                if (statusSearch) {
                    allOf(
                        isAuthorized(user, requireOwnership = false),
                        entity[ShareEntity::state] equal status
                    )
                } else {
                    allOf(isAuthorized(user, requireOwnership = false))
                }
            }
        )
    }

    private fun getSharesStatusFilterPossible(
        session: HibernateSession,
        user: String,
        statusSearch: Boolean,
        paging: NormalizedPaginationRequest,
        status: ShareState? = ShareState.ACCEPTED
    ): List<SharesByPath> {
        val distinctPaths = session.createCriteriaBuilder<String, ShareEntity>().run {
            with(criteria) {
                select(entity[ShareEntity::path])
                distinct(true)

                where(
                    if (statusSearch) {
                        allOf(
                            isAuthorized(user, requireOwnership = false),
                            entity[ShareEntity::state] equal status
                        )
                    } else {
                        allOf(
                            isAuthorized(user, requireOwnership = false)
                        )
                    }
                )

                orderBy(ascending(entity[ShareEntity::path]))
            }
        }.createQuery(session).paginatedList(paging)

        // Retrieve all shares for these paths and group
        val items = session
            .criteria<ShareEntity>(
                orderBy = {
                    listOf(ascending(entity[ShareEntity::filename]))
                },

                predicate = {
                    if (statusSearch) {
                        allOf(
                            entity[ShareEntity::path] isInCollection distinctPaths,
                            isAuthorized(user, requireOwnership = false),
                            entity[ShareEntity::state] equal status
                        )
                    } else {
                        allOf(
                            entity[ShareEntity::path] isInCollection distinctPaths,
                            isAuthorized(user, requireOwnership = false)
                        )
                    }
                }
            )
            .list()
            .map { it.toModel() }
            .let { groupByPath(user, it) }

        return items
    }

    override fun findShareForPath(
        session: HibernateSession,
        user: String,
        path: String
    ): SharesByPath {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(user, requireOwnership = false),
                entity[ShareEntity::path] equal literal(path)
            )
        }.list()
            .map { it.toModel() }
            .takeIf { it.isNotEmpty() }
            ?.let { groupByPath(user, it).single() }
            ?: throw ShareException.NotFound()
    }

    private fun groupByPath(user: String, shares: List<Share>): List<SharesByPath> {
        val byPath = shares.groupBy { it.path }
        return byPath.map { (path, sharesForPath) ->
            val owner = sharesForPath.first().owner
            val sharedByMe = owner == user
            SharesByPath(path, owner, sharedByMe, sharesForPath.map { it.minimalize() })
        }
    }

    override fun create(
        session: HibernateSession,
        user: String,
        share: Share
    ): Long {
        if (user == share.sharedWith) throw ShareException.BadRequest("Cannot share file with yourself")

        val exists = session.criteria<ShareEntity> {
            allOf(
                entity[ShareEntity::path] equal literal(share.path),
                entity[ShareEntity::sharedWith] equal literal(share.sharedWith)
            )
        }.uniqueResult() != null

        if (exists) {
            throw ShareException.DuplicateException()
        }
        return session.save(share.toEntity(copyId = false)) as Long
    }

    override fun updateState(
        session: HibernateSession,
        user: String,
        shareId: Long,
        newState: ShareState
    ): Share {
        val entity = shareById(session, user, shareId, requireOwnership = false)
        entity.state = newState
        entity.modifiedAt = Date()
        session.update(entity)
        return entity.toModel()
    }

    override fun updateRights(
        session: HibernateSession,
        user: String,
        shareId: Long,
        rights: Set<AccessRight>
    ): Share {
        val entity = shareById(session, user, shareId, requireOwnership = true)
        entity.rights = rights.asInt()
        entity.modifiedAt = Date()
        session.update(entity)
        return entity.toModel()
    }

    override fun deleteShare(
        session: HibernateSession,
        user: String,
        shareId: Long
    ): Share {
        val entity = shareById(session, user, shareId, requireOwnership = true)
        session.delete(entity)
        return entity.toModel()
    }

    private fun shareById(
        session: HibernateSession,
        user: String,
        shareId: Long,
        requireOwnership: Boolean
    ): ShareEntity {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(user, requireOwnership),
                entity[ShareEntity::id] equal shareId
            )
        }.uniqueResult() ?: throw ShareException.NotFound()
    }

    private fun CriteriaBuilderContext<*, ShareEntity>.isAuthorized(
        user: String,
        requireOwnership: Boolean
    ): Predicate {
        val isOwner = entity[ShareEntity::owner] equal literal(user)
        val isSharedWith = entity[ShareEntity::sharedWith] equal literal(user)

        val options = arrayListOf(isOwner)
        if (!requireOwnership) options += isSharedWith

        return anyOf(*options.toTypedArray())
    }

    private fun Share.toEntity(copyId: Boolean): ShareEntity {
        return ShareEntity(
            if (copyId) id!!.toLong() else 0,
            owner,
            sharedWith,
            path,
            rights.asInt(),
            Date(),
            Date(),
            state,
            path.substringAfterLast("/")
        )
    }

    private fun ShareEntity.toModel(): Share =
        Share(
            owner,
            sharedWith,
            path,
            rights.asEnumSet(),
            createdAt.time,
            modifiedAt.time,
            state,
            id
        )
}

