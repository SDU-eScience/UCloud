package dk.sdu.cloud.file.orchestrator.service

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.exceptions.DatabaseException
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.Actor
import dk.sdu.cloud.NormalizedPaginationRequestV2
import dk.sdu.cloud.PageV2
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.notification.api.CreateNotification
import dk.sdu.cloud.notification.api.Notification
import dk.sdu.cloud.notification.api.NotificationDescriptions
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class ShareService(
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
    private val backgroundScope: BackgroundScope,
) {
    suspend fun retrieve(
        actor: Actor,
        path: String,
    ): Share {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("shared_with", actor.safeUsername())
                        setParameter("path", path.normalize())
                    },
                    """
                        select * from shares where shared_with = :user and path = :path
                    """
                )
                .rows
                .map { rowToShare(it) }
                .singleOrNull()
                ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }
    }

    suspend fun browse(
        actor: Actor,
        sharedByMe: Boolean,
        filterPath: String?,
        request: NormalizedPaginationRequestV2,
    ): PageV2<Share> {
        return db.paginateV2(
            actor,
            request,
            create = { session ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("shared_with", if (!sharedByMe) actor.safeUsername() else null)
                            setParameter("shared_by", if (sharedByMe) actor.safeUsername() else null)
                            setParameter("filter_path", filterPath)
                        },
                        """
                            declare c cursor for
                            select * from shares
                            where
                                (:shared_with::text is null or shared_with = :shared_with) and
                                (:shared_by::text is null or shared_by = :shared_by) and
                                (
                                    :filter_path::text is null or 
                                    path = :filter_path::text
                                )
                            order by path
                        """
                    )
            },
            mapper = { _, rows ->
                rows.map { rowToShare(it) }
            }
        )
    }

    private fun rowToShare(row: RowData): Share {
        return Share(
            row.getString("path")!!,
            row.getString("shared_by")!!,
            row.getString("shared_with")!!,
            row.getBoolean("approved")!!,
        )
    }

    suspend fun create(
        actor: Actor,
        request: SharesCreateRequest,
    ) {
        // NOTE(Dan): The shares feature is basically just a catalogue of items that some user believes they have
        //   shared with another user. All the updates to the ACL must be done by the client. This also means we
        //   perform no checking here if the share is valid. We don't do this, simply because this communication must
        //   go directly to the updateAcl endpoint. This is required to enforce end-to-end signatures from the user,
        //   which some providers require.
        val userLookup = UserDescriptions.lookupUsers.call(
            LookupUsersRequest(request.items.map { it.sharedWith }.toSet().toList()),
            serviceClient
        ).orThrow()

        for ((user, lookup) in userLookup.results) {
            if (lookup == null) {
                throw RPCException("Unknown user: $user", HttpStatusCode.BadRequest)
            }
        }

        try {
            db.withSession { session ->
                for (reqItem in request.items) {
                    if (reqItem.sharedWith == actor.safeUsername()) {
                        throw RPCException("You cannot share a file with yourself", HttpStatusCode.BadRequest)
                    }

                    session.sendPreparedStatement(
                        {
                            setParameter("shared_by", actor.safeUsername())
                            setParameter("shared_with", reqItem.sharedWith)
                            setParameter("path", reqItem.path)
                        },
                        """
                        insert into shares (path, shared_by, shared_with, approved)  
                        values (:path, :shared_by, :shared_with, false)  
                    """
                    )
                }
            }
        } catch (ex: GenericDatabaseException) {
            if (ex.errorCode == PostgresErrorCodes.UNIQUE_VIOLATION) {
                throw RPCException("Share already exists", HttpStatusCode.Conflict)
            } else {
                throw ex
            }
        }

        backgroundScope.launch {
            runCatching {
                for (reqItem in request.items) {
                    NotificationDescriptions.create.call(
                        CreateNotification(
                            reqItem.sharedWith,
                            Notification(
                                "SHARE_REQUEST",
                                "${actor.safeUsername()} has shared '${reqItem.path.fileName()}' with you"
                            )
                        ),
                        serviceClient
                    )
                }
            }
        }
    }

    suspend fun approve(
        actor: Actor,
        request: SharesApproveRequest,
    ) {
        val sharesAffected = db.withSession { session ->
            request.items.map { reqItem ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("path", reqItem.path.normalize())
                            setParameter("shared_with", actor.safeUsername())
                        },
                        """
                            update shares
                            set approved = true
                            where shared_with = :shared_with and path = :path
                            returning *
                        """
                    )
                    .rows
                    .map { rowToShare(it) }
                    .singleOrNull()
            }
        }.associateBy { it?.path }

        backgroundScope.launch {
            runCatching {
                sharesAffected.forEach { (_, share) ->
                    if (share == null) return@forEach
                    NotificationDescriptions.create.call(
                        CreateNotification(
                            share.sharedBy,
                            Notification(
                                "SHARE_ACCEPTED",
                                "${share.sharedWith} has accepted you share of '${share.path.fileName()}'"
                            )
                        ),
                        serviceClient
                    )
                }
            }
        }
    }

    suspend fun delete(
        actor: Actor,
        request: SharesDeleteRequest,
    ) {
        val sharesAffected = db.withSession { session ->
            request.items.map { reqItem ->
                session
                    .sendPreparedStatement(
                        {
                            setParameter("path", reqItem.path.normalize())
                            setParameter(
                                "shared_with",
                                if (reqItem.sharedWith != null) reqItem.sharedWith else actor.safeUsername()
                            )
                            setParameter(
                                "shared_by",
                                if (reqItem.sharedWith != null) actor.safeUsername() else null
                            )
                        },
                        """
                            delete from shares
                            where 
                                shared_with = :shared_with and 
                                (:shared_by::text is null or shared_by = :shared_by) and
                                path = :path
                            returning *
                        """
                    )
                    .rows
                    .map { rowToShare(it) }
                    .singleOrNull()
            }
        }.associateBy { it?.path }

        backgroundScope.launch {
            runCatching {
                sharesAffected.forEach { (_, share) ->
                    if (share == null) return@forEach
                    val wasRevoked = actor.safeUsername() == share.sharedBy
                    NotificationDescriptions.create.call(
                        CreateNotification(
                            if (wasRevoked) share.sharedWith else share.sharedBy,
                            Notification(
                                "SHARE_DELETED",
                                if (wasRevoked) {
                                    "${share.sharedBy} has delete your share of '${share.path.fileName()}'"
                                } else {
                                    "${share.sharedWith} has delete your share of '${share.path.fileName()}'"
                                },
                                meta = JsonObject(mapOf(
                                    "path" to JsonPrimitive(share.path)
                                ))
                            )
                        ),
                        serviceClient
                    )
                }
            }
        }
    }
}
