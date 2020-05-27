package dk.sdu.cloud.accounting.compute

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.accounting.compute.api.AccountingComputeServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object AccountingComputeService : Service {
    override val description: ServiceDescription = AccountingComputeServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(RefreshingJWTCloudFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    AccountingComputeService.runAsStandalone(args)
}
