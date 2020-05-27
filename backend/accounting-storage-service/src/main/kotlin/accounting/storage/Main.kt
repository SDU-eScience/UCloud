package dk.sdu.cloud.accounting.storage

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.accounting.storage.api.AccountingStorageServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AccountingStorageService : Service {
    override val description: ServiceDescription = AccountingStorageServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AccountingStorageService.runAsStandalone(args)
}
