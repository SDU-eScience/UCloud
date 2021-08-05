package dk.sdu.cloud.sync.mounter 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileSynchronization
import dk.sdu.cloud.file.api.SynchronizationBrowseFoldersRequest
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.sync.mounter.api.MountFolder
import dk.sdu.cloud.sync.mounter.api.MountRequest
import dk.sdu.cloud.sync.mounter.http.MountController
import dk.sdu.cloud.sync.mounter.services.MountService
import kotlinx.coroutines.runBlocking
import java.io.File

class Server(
    override val micro: Micro,
    private val config: SyncMounterConfiguration
) : CommonServer {
    override val log = logger()
    
    override fun start() {
        val mountService = MountService(config)

        with(micro.server) {
            configureControllers(
                MountController(mountService)
            )
        }
        
        startServices()
    }

    override fun onKtorReady() {
        runBlocking {
            val syncFolder = File(config.syncBaseMount ?: "/mnt/sync")
            if (!syncFolder.exists()) {
                syncFolder.mkdir()
            }

            val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
            val mountService = MountService(config)

            val folders = FileSynchronization.browseFolders.call(
                SynchronizationBrowseFoldersRequest(config.deviceId ?: ""),
                client
            ).orThrow()

            mountService.mount(
                MountRequest(
                    folders.map { folder ->
                        MountFolder(folder.id, folder.path)
                    }
                )
            )

            File(joinPath(config.syncBaseMount ?: "/mnt/sync", "ready")).createNewFile()
        }
    }
}