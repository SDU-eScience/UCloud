package dk.sdu.cloud.webdav

import dk.sdu.cloud.ServiceDescription
import dk.sdu.cloud.auth.api.AuthenticatorFeature
import dk.sdu.cloud.webdav.api.WebdavServiceDescription
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer

object WebdavService : Service {
    override val description: ServiceDescription = WebdavServiceDescription

    override fun initializeServer(micro: Micro): CommonServer {
        micro.install(AuthenticatorFeature)
        return Server(micro)
    }
}

fun main(args: Array<String>) {
    WebdavService.runAsStandalone(args)
}
