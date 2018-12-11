package dk.sdu.cloud.accounting.storage.http

import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.accounting.storage.api.StorageAccountingUsageDescription
import dk.sdu.cloud.accounting.storage.services.StorageAccountingService
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.implement
import dk.sdu.cloud.service.securityPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.routing.Route

class StorageAccountingUsageController<DBSession> (
    private val storageAccountingService: StorageAccountingService<DBSession>
) : Controller {

    override val baseContext: String = StorageAccountingUsageDescription.baseContext

    override fun configure(routing: Route): Unit = with(routing) {
        implement(StorageAccountingUsageDescription.collectCurrentStorage) {
            if (call.securityPrincipal.username != "_accounting") {
                return@implement(error(CommonErrorMessage("User Not Allowed"), HttpStatusCode.Unauthorized))
            }
            else {
                val storageUsed = storageAccountingService
                return@implement(ok(storageUsed.collectCurrentStorageUsage()))
            }
        }
    }
}
