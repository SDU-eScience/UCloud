package dk.sdu.cloud.accounting.storage.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.Page
import io.ktor.http.HttpMethod


object StorageAccountingUsageDescription : RESTDescriptions("storageUsage") {
    const val baseContext = "/api/accounting/storage"

    val collectCurrentStorage = callDescription<Unit, Unit, CommonErrorMessage> {
        name = "CollectCurrentStorage"
        method = HttpMethod.Post

        auth {
            access = AccessRight.READ_WRITE
        }

        path {
            using(baseContext)
            +"collectUsage"
        }
    }
}
