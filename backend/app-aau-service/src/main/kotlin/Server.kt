package dk.sdu.cloud.app.aau 

import dk.sdu.cloud.app.aau.rpc.ComputeController
import dk.sdu.cloud.app.aau.services.ResourceCache
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices

class Server(override val micro: Micro) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val serviceClient = micro.authenticator.authenticateClient(OutgoingHttpCall)

        with(micro.server) {
             configureControllers(
                 ComputeController(serviceClient, ResourceCache(serviceClient), micro.developmentModeEnabled),
             )
        }
        
        startServices()
    }
}
