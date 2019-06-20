package dk.sdu.cloud.app.fs.kubernetes

import dk.sdu.cloud.app.fs.kubernetes.rpc.AppFsKubernetesController
import dk.sdu.cloud.app.fs.kubernetes.services.FileSystemService
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import java.io.File

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()

    override fun start() {
        val mountPoint = if (micro.developmentModeEnabled) File("./fs").also { it.mkdir() } else File("/mnt/cephfs")
        val fsService = FileSystemService(mountPoint)
        with(micro.server) {
            configureControllers(
                AppFsKubernetesController(fsService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
    }
}
