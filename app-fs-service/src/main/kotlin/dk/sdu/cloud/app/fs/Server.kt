package dk.sdu.cloud.app.fs

import dk.sdu.cloud.app.fs.rpc.AppFsController
import dk.sdu.cloud.app.fs.services.BackendService
import dk.sdu.cloud.app.fs.services.FileSystemHibernateDao
import dk.sdu.cloud.app.fs.services.SharedFileSystemService
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val fileSystemDao = FileSystemHibernateDao()
        val backendService = BackendService(setOf("kubernetes"), "kubernetes")
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val sharedFileSystemService =
            SharedFileSystemService(backendService, serviceClient, micro.hibernateDatabase, fileSystemDao)

        with(micro.server) {
            configureControllers(
                AppFsController(sharedFileSystemService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
