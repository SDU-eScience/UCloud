package dk.sdu.cloud.file.synchronization

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.synchronization.http.SynchronizationController
import dk.sdu.cloud.file.synchronization.services.SynchronizationService
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import java.io.File

class Server(
    override val micro: Micro,
    private val syncConfig: SynchronizationConfiguration,
    private val cephConfig: CephConfiguration
) : CommonServer {
    override val log = logger()

    override fun start() {
        val client = micro.authenticator.authenticateClient(OutgoingWSCall)
        val db = AsyncDBSessionFactory(micro.databaseConfig)

        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")


        //val fastDirectoryStats = CephFsFastDirectoryStats(nativeFs)
        val syncthingClient = SyncthingClient(syncConfig, db)
        val synchronizationService = SynchronizationService(syncthingClient, fsRootFile.absolutePath, db)

        //with(micro.server) {
            configureControllers(
                SynchronizationController(synchronizationService)
            )
        //}

        startServices()
    }
}
