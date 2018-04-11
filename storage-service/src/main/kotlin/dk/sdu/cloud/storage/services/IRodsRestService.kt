package dk.sdu.cloud.storage.services

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.auth.api.validatedPrincipal
import dk.sdu.cloud.service.RESTHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.storage.services.IRodsRestService.log
import dk.sdu.cloud.storage.services.ext.StorageConnection
import dk.sdu.cloud.storage.services.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.services.ext.StorageException
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory

object IRodsRestService {
    val log = LoggerFactory.getLogger("dk.sdu.cloud.storage.services.IRodsRestServiceKt")!!
}

suspend fun RESTHandler<*, *, CommonErrorMessage>.error(message: String, statusCode: HttpStatusCode) {
    error(CommonErrorMessage(message), statusCode)
}

suspend inline fun RESTHandler<*, *, CommonErrorMessage>.withIRodsConnection(
    storageService: StorageConnectionFactory,
    body: (StorageConnection) -> Unit
) {
    if (!protect()) return

    val principal = call.request.validatedPrincipal
    return try {
        storageService.createForAccount(principal.subject, principal.token).use { body(it) }
    } catch (ex: StorageException) {
        when (ex) {
            is StorageException.BadAuthentication -> error("Unauthorized", HttpStatusCode.Unauthorized)
            is StorageException.Duplicate -> error("Item already exists", HttpStatusCode.Conflict)
            is StorageException.BadPermissions -> error("Not allowed", HttpStatusCode.Forbidden)
            is StorageException.NotFound -> error("Not found", HttpStatusCode.NotFound)
            is StorageException.NotEmpty -> error("Item is not empty", HttpStatusCode.Forbidden)

            is StorageException.BadConnection -> {
                log.warn("Unable to connect to iRODS backend")
                log.warn(ex.stackTraceToString())

                error("Internal Server Error", HttpStatusCode.InternalServerError)
            }
        }
    }
}

