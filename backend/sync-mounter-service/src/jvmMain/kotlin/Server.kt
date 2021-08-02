package dk.sdu.cloud.sync.mounter 

import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.sync.mounter.http.MountController
import dk.sdu.cloud.sync.mounter.services.MountService

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val mountService = MountService()

        with(micro.server) {
            configureControllers(
                MountController(mountService)
            )
        }
        
        startServices()
    }
}