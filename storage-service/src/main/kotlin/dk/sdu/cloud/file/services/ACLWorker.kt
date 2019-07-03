package dk.sdu.cloud.file.services

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.api.UpdateAclRequest
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.background.BackgroundExecutor
import dk.sdu.cloud.file.services.background.BackgroundResponse
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking

private data class UpdateRequest(val request: UpdateAclRequest)

class ACLWorker(
    private val aclService: AclService<*>,
    private val backgroundExecutor: BackgroundExecutor<*>
) {
    fun registerWorkers() {
        backgroundExecutor.addWorker(REQUEST_TYPE) { _, message, user ->
            runBlocking {
                val parsed = defaultMapper.readValue<UpdateRequest>(message)
                val (request) = parsed
                log.debug("Executing ACL update request: $request")

                if (!aclService.isOwner(request.path, user)) {
                    return@runBlocking BackgroundResponse(HttpStatusCode.Forbidden, Unit)
                }

                request.changes.forEach { change ->
                    val entity = FSACLEntity(change.entity)

                    if (change.revoke) {
                        aclService.revokePermission(request.path, entity.user)
                    } else {
                        aclService.updatePermissions(request.path, entity.user, change.rights)
                    }
                }
            }

            BackgroundResponse(HttpStatusCode.OK, Unit)
        }
    }

    suspend fun updateAcl(request: UpdateAclRequest, user: String): String {
        return backgroundExecutor.addJobToQueue(REQUEST_TYPE, UpdateRequest(request), user)
    }

    companion object : Loggable {
        const val REQUEST_TYPE = "updateAcl"
        override val log = logger()
    }
}
