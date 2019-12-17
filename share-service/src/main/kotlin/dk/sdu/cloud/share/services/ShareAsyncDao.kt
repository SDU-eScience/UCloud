package dk.sdu.cloud.share.services

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.fileId
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.asEnumSet
import dk.sdu.cloud.service.asInt
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import org.joda.time.LocalDateTime

object Shares : SQLTable("shares") {
    val owner = varchar("owner", 1024)
    val sharedWith = varchar("shared_with", 1024)
    val path = varchar("path", 4096)
    val filename = varchar("filename", 4096)
    val rights = int("rights")
    val state = int("state")
    val fileId = varchar("file_id", 1024)
    val id = long("id")
    val ownerToken = varchar("owner_token", 4096)
    val recipientToken = varchar("recipient_token", 4096, notNull = false)
    val createdAt = timestamp("created_at")
    val modifiedAt = timestamp("modified_at")
}

class ShareAsyncDao : ShareDAO<AsyncDBConnection> {
    override suspend fun create(
        session: AsyncDBConnection,
        owner: String,
        sharedWith: String,
        path: String,
        initialRights: Set<AccessRight>,
        fileId: String,
        ownerToken: String
    ): Long {
        val allocatedId = session.allocateId()

        try {
            session.insert(Shares) {
                set(Shares.owner, owner)
                set(Shares.sharedWith, sharedWith)
                set(Shares.path, path)
                set(Shares.filename, path.fileName())
                set(Shares.rights, initialRights.asInt())
                set(Shares.state, ShareState.REQUEST_SENT.ordinal)
                set(Shares.fileId, fileId)
                set(Shares.id, allocatedId)
                set(Shares.ownerToken, ownerToken)
                set(Shares.recipientToken, null)

                val now = LocalDateTime.now()
                set(Shares.createdAt, now)
                set(Shares.modifiedAt, now)
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw ShareException.DuplicateException()
            } else {
                log.warn(ex.stackTraceToString())
                throw RPCException("Internal error", HttpStatusCode.InternalServerError)
            }
        }

        return allocatedId
    }

    override suspend fun findById(session: AsyncDBConnection, auth: AuthRequirements, shareId: Long): InternalShare {
        return session
            .sendPreparedStatement(
                {
                    setParameter("authUser", auth.user)
                    setParameter("authRole", auth.requireRole?.name)
                    setParameter("id", shareId)
                },

                """
                    select *
                    from shares s
                    where s.id = ?id and is_share_authorized(s, ?authUser, ?authRole)
                """
            )
            .rows
            .map { it.toInternalShare() }
            .singleOrNull() ?: throw ShareException.NotFound()
    }

    override suspend fun findAllByPath(
        session: AsyncDBConnection,
        auth: AuthRequirements,
        path: String
    ): List<InternalShare> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("authUser", auth.user)
                    setParameter("authRole", auth.requireRole?.name)
                    setParameter("path", path)
                },

                """
                    select *
                    from shares s
                    where
                        s.path = ?path and
                        is_share_authorized(s, ?authUser, ?authRole)
                """
            )
            .rows
            .map { it.toInternalShare() }
            .takeIf { it.isNotEmpty() }
            ?: throw ShareException.NotFound()
    }

    override suspend fun findAllByFileId(
        session: AsyncDBConnection,
        auth: AuthRequirements,
        fileId: String
    ): List<InternalShare> {
        return session
            .sendPreparedStatement(
                {
                    setParameter("authUser", auth.user)
                    setParameter("authRole", auth.requireRole?.name)
                },

                """
                    select *
                    from shares s
                    where
                        s.file_id = ?fileId and is_share_authorized(s, ?authUser, ?authRole)
                """
            )
            .rows
            .map { it.toInternalShare() }
    }

    override suspend fun list(
        session: AsyncDBConnection,
        auth: AuthRequirements,
        shareRelation: ShareRelationQuery,
        paging: NormalizedPaginationRequest
    ): ListSharesResponse {
        val authParameters: EnhancedPreparedStatement.() -> Unit = {
            setParameter("authUser", auth.user)
            setParameter("authRole", auth.requireRole?.name)
            setParameter("username", shareRelation.username)
            setParameter("sharedByMe", shareRelation.sharedByMe)
        }

        val itemsInTotal: Int = session.sendPreparedStatement(
            authParameters,

            """
                select count(distinct path)
                from shares s
                where
                    is_share_authorized(s, ?authUser, ?authRole) and
                    has_share_relation(s, ?username, ?sharedByMe)
            """
        ).rows.single().getAs(0)

        val distinctPaths = session.sendPreparedStatement(
            {
                authParameters()
                setParameter("limit", paging.itemsPerPage)
                setParameter("offset", paging.itemsPerPage * paging.page)
            },

            """
                select distinct s.path 
                from shares s
                where
                    is_share_authorized(s, ?authUser, ?authRole) and
                    has_share_relation(s, ?username, ?sharedByMe)
                limit ?limit
                offset ?offset
            """
        ).rows.map { it.getString(0)!! }

        val relevantShares = session.sendPreparedStatement(
            {
                authParameters()
                setParameter("distinctPaths", distinctPaths)
            },

            """
                select *
                from shares s
                where
                    s.path = any(?distinctPaths) and
                    is_share_authorized(s, ?authUser, ?authRole) and
                    has_share_relation(s, ?username, ?sharedByMe)
            """
        ).rows.map { it.toInternalShare() }

        return ListSharesResponse(relevantShares, itemsInTotal)
    }

    override suspend fun listSharedToMe(
        session: AsyncDBConnection,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<InternalShare> {
        return session.paginatedQuery(
            paging,
            {
                setParameter("user", user)
                setParameter("acceptedState", ShareState.ACCEPTED.ordinal)
            },

            "from shares where shared_with = ?user and state = ?acceptedState"
        ).mapItems { it.toInternalShare() }
    }

    override suspend fun updateShare(
        session: AsyncDBConnection,
        auth: AuthRequirements,
        shareId: Long,
        recipientToken: String?,
        state: ShareState?,
        rights: Set<AccessRight>?,
        path: String?,
        ownerToken: String?
    ) {
        if (path == null && recipientToken == null && state == null && rights == null) {
            throw ShareException.InternalError("Nothing to update")
        }

        // coalesce will select first non-null parameter. This allows us to change the parameter only if a parameter
        // was changed.
        session
            .sendPreparedStatement(
                {
                    setParameter("authUser", auth.user)
                    setParameter("authRole", auth.requireRole?.name)

                    setParameter("id", shareId)
                    setParameter("path", path)
                    setParameter("filename", path?.fileName())
                    setParameter("recipientToken", recipientToken)
                    setParameter("ownerToken", ownerToken)
                    setParameter("state", state?.ordinal)
                    setParameter("rights", rights?.asInt())
                },

                """
                    update shares s
                    set
                        path = coalesce(?path, path),
                        filename = coalesce(?filename, filename),
                        recipient_token = coalesce(?recipientToken, recipient_token),
                        owner_token = coalesce(?ownerToken, owner_token),
                        state = coalesce(?state, state),
                        rights = coalesce(?rights, rights)
                    where
                        is_share_authorized(s, ?authUser, ?authRole) and s.id = ?id
                """
            )
    }

    override suspend fun deleteShare(session: AsyncDBConnection, auth: AuthRequirements, shareId: Long) {
        session
            .sendPreparedStatement(
                {
                    setParameter("shareId", shareId)
                    setParameter("authUser", auth.user)
                    setParameter("authRole", auth.requireRole?.name)
                },

                """
                    delete from shares s
                    where 
                        s.id = ?shareId and 
                        is_share_authorized(s, ?authUser, ?authRole)
                """
            )
    }

    override suspend fun onFilesMoved(
        session: AsyncDBConnection,
        events: List<StorageEvent.Moved>
    ) {
        val eventsByFileId = events.associateBy { it.file.fileId }
        val fileIds = eventsByFileId.keys.toList() // Force a particular order
        val paths = fileIds.map { eventsByFileId.getValue(it).file.path }
        val filenames = fileIds.map { eventsByFileId.getValue(it).file.path.fileName() }

        session.sendPreparedStatement(
            {
                setParameter("paths", paths)
                setParameter("fileIds", fileIds)
                setParameter("filenames", filenames)
            },

            """
                update shares
                set
                    path = data.path,
                    filename = data.filename
                from
                    (
                        select 
                            unnest(?paths::text[]) as path,
                            unnest(?filenames::text[]) as filename,
                            unnest(?fileIds::text[]) as file_id
                    ) as data
                where
                    shares.file_id = data.file_id
            """
        )
    }

    override suspend fun findAllByFileIds(
        session: AsyncDBConnection,
        fileIds: List<String>
    ): List<InternalShare> {
        require(fileIds.size <= 250) { "fileIds.size > 250 " }
        return session.sendPreparedStatement(
            { setParameter("fileIds", fileIds) },
            """
                select *
                from shares s
                where s.file_id = any(?fileIds)
            """
        ).rows.map { it.toInternalShare() }
    }

    override suspend fun deleteAllByShareId(session: AsyncDBConnection, shareIds: List<Long>) {
        session
            .sendPreparedStatement(
                {
                    setParameter("shareIds", shareIds)
                },

                """
                    delete from shares
                    where id = any(?shareIds)
                """
            )
    }

    @UseExperimental(ExperimentalCoroutinesApi::class)
    override suspend fun listAll(
        scope: CoroutineScope,
        session: AsyncDBConnection
    ): ReceiveChannel<InternalShare> = scope.produce {
        session
            .sendQuery(
                """
                    declare all_shares no scroll cursor for
                    select *
                    from shares s
                """
            )

        try {
            while (true) {
                val newRows = session
                    .sendQuery("fetch forward 50 from all_shares")
                    .rows
                    .map { it.toInternalShare() }

                if (newRows.isEmpty()) break
                newRows.forEach { send(it) }
            }
        } finally {
            session.sendQuery("close all_shares")
        }
    }


    private fun RowData.toInternalShare(): InternalShare {
        return InternalShare(
            getField(Shares.id),
            getField(Shares.owner),
            getField(Shares.sharedWith),
            ShareState.values()[getField(Shares.state)],
            getField(Shares.path),
            getField(Shares.rights).asEnumSet(),
            getField(Shares.fileId),
            getField(Shares.ownerToken),
            getFieldNullable(Shares.recipientToken),
            getField(Shares.createdAt).toDate().time,
            getField(Shares.modifiedAt).toDate().time
        )
    }

    companion object : Loggable {
        override val log = logger()
    }
}
