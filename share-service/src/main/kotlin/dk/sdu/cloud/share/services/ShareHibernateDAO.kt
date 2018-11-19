package dk.sdu.cloud.share.services

import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.asEnumSet
import dk.sdu.cloud.service.asInt
import dk.sdu.cloud.service.db.CriteriaBuilderContext
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.service.db.countWithPredicate
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createCriteriaDelete
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import dk.sdu.cloud.service.db.paginatedList
import dk.sdu.cloud.share.api.ShareState
import java.io.File
import java.lang.IllegalArgumentException
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
    var owner: String,

    @Column(name = COL_SHARED_WITH)
    var sharedWith: String,

    @Column(name = COL_PATH)
    var path: String,

    var rights: Int,

    @Enumerated(EnumType.ORDINAL)
    var state: ShareState,

    var fileId: String,

    @Id
    @GeneratedValue
    var id: Long = 0,

    var ownerToken: String,

    var recipientToken: String? = null,

    @Temporal(TemporalType.TIMESTAMP)
    override var createdAt: Date = Date(System.currentTimeMillis()),

    @Temporal(TemporalType.TIMESTAMP)
    override var modifiedAt: Date = Date(System.currentTimeMillis())
) : WithTimestamps {
    val filename: String
        get() = File(path).name

    companion object : HibernateEntity<ShareEntity>, WithId<Long>
}

class ShareHibernateDAO : ShareDAO<HibernateSession> {
    override fun create(
        session: HibernateSession,
        owner: String,
        sharedWith: String,
        path: String,
        initialRights: Set<AccessRight>,
        fileId: String,
        ownerToken: String
    ): Long {
        if (owner == sharedWith) throw ShareException.BadRequest("Cannot share file with yourself")

        val exists = session.criteria<ShareEntity> {
            allOf(
                entity[ShareEntity::path] equal literal(path),
                entity[ShareEntity::sharedWith] equal literal(sharedWith)
            )
        }.uniqueResult() != null

        if (exists) {
            throw ShareException.DuplicateException()
        }

        return session.save(
            ShareEntity(
                owner = owner,
                sharedWith = sharedWith,
                path = path,
                state = ShareState.REQUEST_SENT,
                rights = initialRights.asInt(),
                fileId = fileId,
                ownerToken = ownerToken
            )
        ) as Long
    }

    override fun findAllByFileId(
        session: HibernateSession,
        auth: AuthRequirements,
        fileId: String
    ): List<InternalShare> {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(auth),
                entity[ShareEntity::fileId] equal fileId
            )
        }.list().map { it.toModel() }
    }

    override fun findById(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare {
        return shareById(session, auth, shareId).toModel()
    }

    override fun findAllByPath(
        session: HibernateSession,
        auth: AuthRequirements,
        path: String
    ): List<InternalShare> {
        return session.criteria<ShareEntity> {
            allOf(
                isAuthorized(auth),
                entity[ShareEntity::path] equal literal(path)
            )
        }.list()
            .map { it.toModel() }
            .takeIf { it.isNotEmpty() }
            ?: throw ShareException.NotFound()
    }

    override fun list(
        session: HibernateSession,
        auth: AuthRequirements,
        state: ShareState?,
        paging: NormalizedPaginationRequest
    ): ListSharesResponse {
        val itemsInTotal = countShareGroups(session, auth, state)
        val items = findShareGroups(session, auth, paging, state)

        return ListSharesResponse(
            items,
            itemsInTotal.toInt()
        )
    }

    private fun countShareGroups(
        session: HibernateSession,
        auth: AuthRequirements,
        state: ShareState?
    ): Long {
        return session.countWithPredicate<ShareEntity>(
            distinct = true,
            selection = { entity[ShareEntity::path] },
            predicate = { findSharesBy(auth, state) }
        )
    }

    private fun findShareGroups(
        session: HibernateSession,
        auth: AuthRequirements,
        paging: NormalizedPaginationRequest,
        state: ShareState?
    ): List<InternalShare> {
        // We first find the share groups (by path)
        val distinctPaths = session.createCriteriaBuilder<String, ShareEntity>().run {
            with(criteria) {
                select(entity[ShareEntity::path])
                distinct(true)
                where(findSharesBy(auth, state))
                orderBy(ascending(entity[ShareEntity::path]))
            }
        }.createQuery(session).paginatedList(paging)

        // We then retrieve all shares for each group
        return session
            .criteria<ShareEntity>(
                orderBy = { listOf(ascending(entity[ShareEntity::filename])) },
                predicate = {
                    allOf(
                        entity[ShareEntity::path] isInCollection distinctPaths,
                        findSharesBy(auth, state)
                    )
                }
            )
            .list()
            .map { it.toModel() }
    }

    override fun updateShare(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long,
        recipientToken: String?,
        state: ShareState?,
        rights: Set<AccessRight>?,
        path: String?
    ): InternalShare {
        if (path == null && recipientToken == null && state == null && rights == null) {
            throw ShareException.InternalError("Nothing to update")
        }

        val share = shareById(session, auth, shareId)
        if (path != null) {
            share.path = path
        }
        if (recipientToken != null) share.recipientToken = recipientToken
        if (state != null) share.state = state
        if (rights != null) share.rights = rights.asInt()

        session.save(share)
        return share.toModel()
    }

    override fun deleteShare(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): InternalShare {
        val entity = shareById(session, auth, shareId)
        session.delete(entity)
        return entity.toModel()
    }

    override fun onFilesMoved(session: HibernateSession, events: List<StorageEvent.Moved>): List<InternalShare> {
        val shares = internalFindAllByFileId(session, events.map { it.id })
        if (shares.isNotEmpty()) {
            val eventsByFileId = events.associateBy { it.id }
            shares.forEach { share ->
                val event = eventsByFileId[share.fileId] ?: return@forEach
                share.path = event.path
                session.save(share)
            }
        }
        session.flush()
        return shares.map { it.toModel() }
    }

    override fun findAllByFileIds(session: HibernateSession, fileIds: List<String>): List<InternalShare> {
        return internalFindAllByFileId(session, fileIds).map { it.toModel() }
    }

    override fun deleteByFileIds(session: HibernateSession, fileIds: List<String>) {
        session.deleteCriteria<ShareEntity> { entity[ShareEntity::fileId] isInCollection fileIds }.executeUpdate()
    }

    private fun internalFindAllByFileId(session: HibernateSession, fileIds: List<String>): List<ShareEntity> {
        if (fileIds.size > 250) throw IllegalArgumentException("fileIds.size > 250")
        return session.criteria<ShareEntity> { entity[ShareEntity::fileId] isInCollection fileIds }.list()
    }

    private fun shareById(
        session: HibernateSession,
        auth: AuthRequirements,
        shareId: Long
    ): ShareEntity {
        return session.criteria<ShareEntity> {
            allOf(isAuthorized(auth), entity[ShareEntity::id] equal shareId)
        }.uniqueResult() ?: throw ShareException.NotFound()
    }

    private fun CriteriaBuilderContext<*, ShareEntity>.isAuthorized(
        auth: AuthRequirements
    ): Predicate {
        if (auth.user == null || auth.requireRole == null) return literal(true).toPredicate()

        val isOwner = entity[ShareEntity::owner] equal literal(auth.user)
        val isSharedWith = entity[ShareEntity::sharedWith] equal literal(auth.user)

        return when (auth.requireRole) {
            ShareRole.PARTICIPANT -> anyOf(isOwner, isSharedWith)
            ShareRole.OWNER -> isOwner
            ShareRole.RECIPIENT -> isSharedWith
        }
    }

    private fun CriteriaBuilderContext<*, ShareEntity>.findSharesBy(
        auth: AuthRequirements,
        state: ShareState?
    ): Predicate {
        val predicates = arrayListOf(isAuthorized(auth))
        if (state != null) {
            predicates.add(entity[ShareEntity::state] equal state)
        }
        return allOf(*predicates.toTypedArray())
    }

    private fun ShareEntity.toModel(): InternalShare =
        InternalShare(
            id = id,
            owner = owner,
            sharedWith = sharedWith,
            state = state,
            path = path,
            rights = rights.asEnumSet(),
            fileId = fileId,
            ownerToken = ownerToken,
            recipientToken = recipientToken,
            createdAt = createdAt.time,
            modifiedAt = modifiedAt.time
        )
}

