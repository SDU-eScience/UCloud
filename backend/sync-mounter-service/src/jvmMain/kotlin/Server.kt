package dk.sdu.cloud.sync.mounter 

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.sync.mounter.api.*
import dk.sdu.cloud.sync.mounter.http.MountController
import dk.sdu.cloud.sync.mounter.services.MountService
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class Server(
    override val micro: Micro,
    private val config: SyncMounterConfiguration
) : CommonServer {
    override val log = logger()
    private val ready: AtomicBoolean = AtomicBoolean(false)
    
    override fun start() {
        val mountService = MountService(config, ready)

        with(micro.server) {
            configureControllers(
                MountController(mountService)
            )
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            runBlocking {
                File(joinPath(config.syncBaseMount, "ready")).delete()

                val syncDir = File(joinPath(config.syncBaseMount))

                mountService.unmount(
                    UnmountRequest(
                        syncDir.listFiles().map { file ->
                            MountFolderId(file.name)
                        }
                    )
                )
            }
        })
        
        startServices()
    }

    override fun onKtorReady() {
        runBlocking {
            val readyFile = File(joinPath(config.syncBaseMount, "ready"))
            readyFile.deleteOnExit()

            try {
                val syncFolder = File(config.syncBaseMount)
                if (!syncFolder.exists()) {
                    syncFolder.mkdir()
                }

                val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
                val mountService = MountService(config, ready)

                val folders = UCloudBrowseSyncFolders.browse.call(
                    UCloudSyncFoldersBrowseRequest(config.deviceId),
                    client
                ).orThrow()

                mountService.mount(
                    MountRequest(
                        folders.map { folder ->
                            MountFolder(folder.id, folder.path)
                        }
                    )
                )
            } catch (ex: Throwable) {
                log.warn("Caught exception while initializing mounter (is file-ucloud down?)")
                log.warn(ex.stackTraceToString())
            }

            readyFile.createNewFile()
            ready.set(true)
        }
    }
}
