package dk.sdu.cloud.share.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.LookupUsersRequest
import dk.sdu.cloud.auth.api.UserDescriptions
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.server.requiredAuthScope
import dk.sdu.cloud.contact.book.api.ContactBookDescriptions
import dk.sdu.cloud.contact.book.api.InsertRequest
import dk.sdu.cloud.contact.book.api.ServiceOrigin
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.share.api.ShareState
import dk.sdu.cloud.share.api.Shares
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ShareService(
    private val serviceClient: AuthenticatedClient,
    private val userClientFactory: (refreshToken: String) -> AuthenticatedClient
) {
    suspend fun create(
        user: String,
        share: Shares.Create.Request,
        userToken: String,
        userCloud: AuthenticatedClient
    ) {
        log.debug("Creating share for $user $share")

        if (user == share.sharedWith) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Users cannot share with themselves")
        }

        coroutineScope {
            log.debug("Verifying file exists")
            val statJob = async {
                FileDescriptions.stat
                    .call(StatRequest(share.path), userCloud)
                    .orNull()
            }

            log.debug("Verifying that user exists")
            val lookupJob = launch {
                val lookup = UserDescriptions.lookupUsers.call(
                    LookupUsersRequest(listOf(share.sharedWith)),
                    serviceClient
                ).orRethrowAs { throw ShareException.InternalError("Could not look up user") }
                lookup.results[share.sharedWith]
                    ?: throw ShareException.BadRequest("The user you are attempting to share with does not exist")
                if (lookup.results[share.sharedWith]?.role == Role.SERVICE) {
                    throw ShareException.BadRequest("The user you are attempting to share with does not exist")
                }
            }

            // Join tasks
            lookupJob.join()
            val file = statJob.await() ?: throw ShareException.NotFound()

            // Verify results.
            if (file.ownerName != user) {
                throw ShareException.NotAllowed()
            }

            launch {
                ContactBookDescriptions.insert.call(
                    InsertRequest(user, listOf(share.sharedWith), ServiceOrigin.SHARE_SERVICE), serviceClient
                )
            }
        }

        val ownerToken = createToken(
            serviceClient, userToken, listOf(
                FileDescriptions.updateAcl.requiredAuthScope
            )
        )

        try {
            updateMetadata(
                share.path,
                InternalShare(
                    sharedBy = user,
                    sharedWith = share.sharedWith,
                    rights = share.rights,
                    state = ShareState.REQUEST_SENT,
                    ownerToken = ownerToken,
                    recipientToken = null
                ),
                updateIfExists = false
            )

            aSendCreatedNotification(serviceClient, user, share)
        } catch (ex: Throwable) {
            revokeToken(serviceClient, ownerToken)
            throw ex
        }
    }

    suspend fun acceptShare(
        user: String,
        path: String,
        userToken: String
    ) {
        val existingShare = getMetadata(path, user)
        if (existingShare.state != ShareState.REQUEST_SENT) {
            throw RPCException("Share has already been accepted.", HttpStatusCode.BadRequest)
        }

        val tokenExtension = createToken(
            serviceClient, userToken, listOf(
                FileDescriptions.stat.requiredAuthScope,
                FileDescriptions.deleteFile.requiredAuthScope
            )
        )

        updateMetadata(
            path,
            existingShare.copy(
                state = ShareState.UPDATING,
                recipientToken = tokenExtension
            ),
            updateIfExists = true
        )

        try {
            updateFSPermissions(path, existingShare)
        } catch (ex: Throwable) {
            updateMetadata(
                path,
                existingShare.copy(
                    state = ShareState.REQUEST_SENT,
                    recipientToken = null
                ),
                updateIfExists = true
            )

            throw ex
        }

        updateMetadata(
            path,
            existingShare.copy(
                state = ShareState.ACCEPTED,
                recipientToken = tokenExtension
            ),
            updateIfExists = true
        )
    }

    suspend fun updateRights(
        user: String,
        path: String,
        sharedWith: String,
        newRights: Set<AccessRight>
    ) {
        val existingShare = getMetadata(path, sharedWith)
        if (existingShare.sharedBy != user) throw RPCException.fromStatusCode(HttpStatusCode.NotFound)

        when (existingShare.state) {
            ShareState.UPDATING -> {
                throw RPCException("Share is currently updating", HttpStatusCode.BadRequest)
            }

            ShareState.REQUEST_SENT -> {
                updateMetadata(
                    path,
                    existingShare.copy(rights = newRights),
                    updateIfExists = true
                )
            }

            ShareState.ACCEPTED -> {
                updateMetadata(path, existingShare.copy(state = ShareState.UPDATING), updateIfExists = true)

                try {
                    updateFSPermissions(path, existingShare, newRights = newRights)
                    updateMetadata(
                        path,
                        existingShare.copy(state = ShareState.ACCEPTED, rights = newRights),
                        updateIfExists = true
                    )
                } catch (ex: Throwable) {
                    // Go back to accepted with old rights
                    updateMetadata(path, existingShare.copy(state = ShareState.ACCEPTED), updateIfExists = true)
                }
            }
        }
    }

    // Share deletions
    suspend fun deleteShare(user: String, path: String, sharedWith: String) {
        val existingShare = getMetadata(path, sharedWith)
        if (user != existingShare.sharedBy && user != existingShare.sharedWith) {
            throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        }

        log.debug("Deleting share $existingShare")
        if (existingShare.state == ShareState.UPDATING) {
            throw ShareException.BadRequest("Cannot delete share while it is updating!")
        }

        try {
            updateFSPermissions(path, existingShare, revoke = true)
        } catch (ex: RPCException) {
            if (ex.httpStatusCode == HttpStatusCode.NotFound) {
                invalidateShare(existingShare)
                return
            } else {
                throw ex
            }
        }
        invalidateShare(existingShare)
        deleteMetadata(path, sharedWith)
    }

    private suspend fun updateMetadata(path: String, share: InternalShare, updateIfExists: Boolean) {
        val updates = listOf(
            MetadataUpdate(
                path,
                METADATA_TYPE_SHARES,
                share.sharedWith,
                defaultMapper.writeValueAsString(share)
            )
        )

        if (updateIfExists) {
            MetadataDescriptions.updateMetadata.call(UpdateMetadataRequest(updates), serviceClient).orThrow()
        } else {
            val resp = MetadataDescriptions.createMetadata.call(CreateMetadataRequest(updates), serviceClient)
            if (!resp.statusCode.isSuccess()) {
                if (resp.statusCode == HttpStatusCode.Conflict) {
                    throw RPCException("File has already been shared with this user", HttpStatusCode.Conflict)
                } else {
                    log.warn("Received bad response from server: ${resp.statusCode}")
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    private suspend fun getMetadata(path: String, sharedWith: String): InternalShare {
        val metadata = MetadataDescriptions.findMetadata.call(
            FindMetadataRequest(
                path,
                METADATA_TYPE_SHARES,
                sharedWith
            ),
            serviceClient
        ).orThrow().metadata.firstOrNull() ?: throw RPCException(
            "Could not find share information",
            HttpStatusCode.NotFound
        )

        return defaultMapper.readValue(metadata.jsonPayload)
    }

    private suspend fun deleteMetadata(path: String, sharedWith: String) {
        MetadataDescriptions.removeMetadata.call(
            RemoveMetadataRequest(listOf(FindMetadataRequest(path, METADATA_TYPE_SHARES, sharedWith))),
            serviceClient
        ).orThrow()
    }

    private suspend fun invalidateShare(share: InternalShare) {
        coroutineScope {
            listOf(
                launch { revokeToken(serviceClient, share.ownerToken) },
                launch { revokeToken(serviceClient, share.recipientToken) }
            ).joinAll()
        }
    }

    private suspend fun updateFSPermissions(
        path: String,
        existingShare: InternalShare,
        revoke: Boolean = false,
        newRights: Set<AccessRight> = existingShare.rights
    ) {
        val userCloud = userClientFactory(existingShare.ownerToken)
        FileDescriptions.updateAcl.call(
            UpdateAclRequest(
                path,
                listOf(
                    ACLEntryRequest(
                        ACLEntity.User(existingShare.sharedWith),
                        newRights,
                        revoke = revoke
                    )
                )
            ), userCloud
        ).orThrow()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
