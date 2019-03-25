package dk.sdu.cloud.accounting

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.startServices

class Server(
    override val micro: Micro
) : CommonServer {
    override val log = logger()

    override fun start() {
        startServices()
    }
}
