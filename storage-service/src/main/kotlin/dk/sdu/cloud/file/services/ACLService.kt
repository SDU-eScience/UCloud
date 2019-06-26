package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.api.ACLEntryRequest
import dk.sdu.cloud.file.api.AccessRight
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.services.background.BackgroundExecutor
import dk.sdu.cloud.file.services.background.BackgroundResponse
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.unwrap
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.http.HttpStatusCode

private data class UpdateRequestWithRealOwner(val request: UpdateAclRequest, val realOwner: String)

class ACLService<Ctx : FSUserContext>(
    private val fsCommandRunnerFactory: FSCommandRunnerFactory<Ctx>,
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val backgroundExecutor: BackgroundExecutor<*>
) {
    private suspend fun grantRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        rights: Set<AccessRight>,
        realOwner: String
    ) {
        suspend fun grant(entity: FSACLEntity, defaultList: Boolean) {
            fs.createACLEntry(
                ctx,
                path,
                entity,
                rights,
                defaultList = defaultList,
                transferOwnershipTo = null
            ).setfaclUnwrap()
        }

        // Add to both the default and the actual list. This needs to be recursively applied
        // We need to apply this process to both the creator and the entity.
        grant(FSACLEntity.User(realOwner), false)
        grant(FSACLEntity.User(realOwner), true)

        grant(entity, false)
        grant(entity, true)
    }

    private suspend fun revokeRights(
        ctx: Ctx,
        path: String,
        entity: FSACLEntity,
        realOwner: String
    ) {
        suspend fun revoke(defaultList: Boolean) {
            fs.removeACLEntry(
                ctx,
                path,
                entity,
                defaultList = defaultList,
                transferOwnershipTo = realOwner
            ).setfaclUnwrap()
        }

        revoke(false)
        revoke(true)
    }

    private fun <T> FSResult<T>.setfaclUnwrap(): T {
        if (statusCode == 256) {
            throw FSException.NotFound()
        }

        return unwrap()
    }

    fun registerWorkers() {
        backgroundExecutor.addWorker(REQUEST_TYPE) { _, message ->
            fsCommandRunnerFactory.withBlockingContext(SERVICE_USER) { ctx ->
                val initiatedChanges = ArrayList<ACLEntryRequest>()
                var parsedRequest: UpdateRequestWithRealOwner? = null

                try {
                    val parsed = defaultMapper.readValue<UpdateRequestWithRealOwner>(message)
                    parsedRequest = parsed
                    val (request, realOwner) = parsed
                    log.debug("Executing ACL update request: $request")

                    request.changes.forEach { change ->
                        // We roll all changes back which have been initiated.
                        // It is safe to perform reverts for the actions that may not have been performed yet.
                        initiatedChanges.add(change)

                        val entity =
                            if (change.isUser) FSACLEntity.User(change.entity) else FSACLEntity.Group(change.entity)

                        if (change.revoke) {
                            revokeRights(ctx, request.path, entity, realOwner)
                        } else {
                            grantRights(ctx, request.path, entity, change.rights, realOwner)
                        }
                    }

                    BackgroundResponse(HttpStatusCode.OK, Unit)
                } catch (ex: Exception) {
                    log.info("Caught exception while updating ACL!")
                    log.info(ex.stackTraceToString())

                    // Rollback changes that were applied
                    if (parsedRequest != null && parsedRequest.request.automaticRollback != false) {
                        val negatedChanges = initiatedChanges.map { change ->
                            change.copy(revoke = !change.revoke)
                        }

                        if (negatedChanges.isNotEmpty()) {
                            log.debug("Rolling changes back!")
                            updateAcl(
                                UpdateAclRequest(
                                    parsedRequest.request.path,
                                    negatedChanges,
                                    automaticRollback = false
                                ),
                                parsedRequest.realOwner,
                                SERVICE_USER
                            )
                        }
                    }

                    if (parsedRequest != null && parsedRequest.request.automaticRollback == false) {
                        log.warn("Unable to rollback changes for $parsedRequest")
                        log.warn("The FS ACL will be out of sync!")
                    }

                    if (ex is RPCException) {
                        BackgroundResponse(ex.httpStatusCode, CommonErrorMessage(ex.why))
                    } else {
                        BackgroundResponse(HttpStatusCode.InternalServerError, CommonErrorMessage("Internal error"))
                    }
                }
            }
        }
    }

    suspend fun updateAcl(request: UpdateAclRequest, realOwner: String, user: String): String {
        return backgroundExecutor.addJobToQueue(REQUEST_TYPE, UpdateRequestWithRealOwner(request, realOwner), user)
    }

    companion object : Loggable {
        const val REQUEST_TYPE = "updateAcl"
        override val log = logger()
    }
}
