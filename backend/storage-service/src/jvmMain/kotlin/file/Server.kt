package dk.sdu.cloud.file

import com.sun.jna.Platform
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.http.*
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.file.processors.ProjectProcessor
import dk.sdu.cloud.file.synchronization.services.SyncthingClient
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.sync.mounter.api.Mounts
import dk.sdu.cloud.sync.mounter.api.ReadyRequest
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.slf4j.Logger
import java.io.File
import kotlin.system.*

class Server(
    private val config: StorageConfiguration,
    private val cephConfig: CephConfiguration,
    override val micro: Micro,
    private val syncConfig: SynchronizationConfiguration
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        require(Platform.isLinux() || micro.developmentModeEnabled) {
            "This service is only able to run on GNU/Linux in production mode"
        }

        // FS root
        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")
        val cephFsRootPath = (cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder

        log.info("Serving files from ${fsRootFile.absolutePath}")

        val homeFolderService = HomeFolderService()
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val metadataDao = MetadataDao()
        val metadataService = MetadataService(db, metadataDao)
        val projectCache = ProjectCache(client)
        val syncthingClient = SyncthingClient(syncConfig, db)
        val newAclService = AclService(metadataService, homeFolderService, client, projectCache, db, syncthingClient)
        val synchronizationService = SynchronizationService(syncthingClient, fsRootFile.absolutePath, db, newAclService, client)

        val processRunner = LinuxFSRunnerFactory(micro.backgroundScope)
        val fs = LinuxFS(fsRootFile, newAclService, cephConfig)
        val limitChecker = LimitChecker(db, newAclService, projectCache, client, config.product, fs, processRunner)
        val coreFileSystem =
            CoreFileSystemService(fs, wsClient, micro.backgroundScope, metadataService, limitChecker)

        val fileLookupService = FileLookupService(processRunner, coreFileSystem)
        val indexingService = IndexingService<LinuxFSRunner>(newAclService)

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        if (micro.commandLineArguments.contains("--scan-accounting")) {
            try {
                AccountingScan(fs, processRunner, client, db).scan()
                exitProcess(0)
            } catch (throwable: Throwable){
                throwable.printStackTrace()
                exitProcess(1)
            }
        }

        UserProcessor(
            streams,
            fsRootFile,
            homeFolderService
        ).init()

        ProjectProcessor(streams, fsRootFile, client).init()

        val metadataRecovery = MetadataRecoveryService(
            micro.backgroundScope,
            DistributedLockBestEffortFactory(micro),
            coreFileSystem,
            processRunner,
            db,
            metadataDao
        )

        metadataRecovery.startProcessing()

        val tokenValidation =
            micro.tokenValidation as? TokenValidationJWT ?: throw IllegalStateException("JWT token validation required")

        // HTTP
        with(micro.server) {
            configureControllers(
                ActionController(
                    commandRunnerForCalls,
                    coreFileSystem,
                    fileLookupService,
                    limitChecker
                ),

                LookupController(
                    commandRunnerForCalls,
                    fileLookupService,
                    homeFolderService
                ),

                FileSecurityController(
                    commandRunnerForCalls,
                    newAclService,
                    coreFileSystem,
                    config.filePermissionAcl + if (micro.developmentModeEnabled) setOf("admin@dev") else emptySet()
                ),

                IndexingController(
                    processRunner,
                    indexingService
                ),

                SimpleDownloadController(
                    client,
                    commandRunnerForCalls,
                    coreFileSystem,
                    tokenValidation,
                    fileLookupService,
                    cephFsRootPath
                ),

                MultiPartUploadController(
                    client,
                    commandRunnerForCalls,
                    coreFileSystem,
                    micro.backgroundScope
                ),

                ExtractController(
                    client,
                    coreFileSystem,
                    commandRunnerForCalls,
                    micro.backgroundScope
                ),

                MetadataController(metadataService, metadataRecovery),

                SynchronizationController(synchronizationService)
            )
        }

        startServices()
    }

    override fun onKtorReady() {
        runBlocking {
            val running: List<LocalSyncthingDevice> = syncConfig.devices.mapNotNull { device ->
                val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
                var foldersMounted = false
                var retryCount = 0

                while (!foldersMounted && retryCount < 5) {
                    delay(1000L)
                    retryCount += 1

                    val ready = Mounts.ready.call(
                        Unit,
                        client
                    )

                    if (ready.statusCode == HttpStatusCode.OK) {
                        if (ready.orThrow().ready) {
                            foldersMounted = true
                        }
                    }
                }

                if (foldersMounted) {
                    device
                } else {
                    null
                }
            }

            val db = AsyncDBSessionFactory(micro.databaseConfig)
                val syncthingClient = SyncthingClient(syncConfig, db)
                syncthingClient.writeConfig(running)
                syncthingClient.rescan(running)
        }
    }
}
