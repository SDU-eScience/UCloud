package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingCallFilter
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.http.*
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.*
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File
import java.io.FileInputStream
import kotlin.system.exitProcess

class Server(
    private val config: StorageConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        val streams = micro.eventStreamService
        val cloud = micro.authenticator.authenticateClient(OutgoingHttpCall)

        log.info("Creating core services")


        BackgroundScope.init()

        // Authentication
        val useFakeUsers = micro.developmentModeEnabled && !micro.commandLineArguments.contains("--real-users")
        val uidLookupService =
            if (useFakeUsers) DevelopmentUIDLookupService("admin@dev") else AuthUIDLookupService(cloud)

        // FS root
        val fsRootFile = File("/mnt/cephfs/").takeIf { it.exists() }
            ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")
        val fsRoot = fsRootFile.normalize().absolutePath

        // Low level FS
        val processRunner = LinuxFSRunnerFactory(uidLookupService)
        val fs = LinuxFS(processRunner, fsRootFile, uidLookupService)

        // High level FS
        val storageEventProducer = StorageEventProducer(streams.createProducer(StorageEvents.events)) {
            log.warn("Caught exception while emitting a storage event!")
            log.warn(it.stackTraceToString())
            stop()
        }
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer)

        // Bulk operations
        val bulkDownloadService = BulkDownloadService(coreFileSystem)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(coreFileSystem)
        val indexingService = IndexingService(processRunner, coreFileSystem, storageEventProducer)
        val fileScanner = FileScanner(processRunner, coreFileSystem, storageEventProducer)
        val workspaceService = WorkspaceService(fsRootFile, fileScanner)

        // Metadata services
        val aclService = ACLService(fs)
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)
        val homeFolderService = HomeFolderService(cloud)

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        log.info("Core services constructed!")

        if (micro.commandLineArguments.contains("--bug-test")) {
            ZipBulkUploader.upload(
                cloud,
                coreFileSystem,
                { processRunner("dthrane@imada.sdu.dk") },
                "/home/dthrane@imada.sdu.dk/foobar",
                WriteConflictPolicy.OVERWRITE,
                FileInputStream("/tmp/myupload.zip"),
                null,
                sensitivityService,
                "foo.zip"
            )
            exitProcess(0)
        }

        UserProcessor(
            streams,
            uidLookupService,
            fileScanner,
            processRunner,
            coreFileSystem
        ).init()

        val tokenValidation =
            micro.tokenValidation as? TokenValidationJWT ?: throw IllegalStateException("JWT token validation required")

        // HTTP
        with(micro.server) {
            attachFilter(IngoingCallFilter.afterParsing(HttpCall) { _, _ ->
                val principalOrNull = runCatching { securityPrincipal }.getOrNull()
                if (principalOrNull != null) {
                    uidLookupService.storeMapping(principalOrNull.username, principalOrNull.uid)
                }
            })

            configureControllers(
                ActionController(
                    commandRunnerForCalls,
                    coreFileSystem,
                    sensitivityService,
                    fileLookupService
                ),

                LookupController(
                    commandRunnerForCalls,
                    fileLookupService,
                    homeFolderService
                ),

                FileSecurityController(
                    commandRunnerForCalls,
                    coreFileSystem,
                    aclService,
                    sensitivityService,
                    config.filePermissionAcl
                ),

                IndexingController(
                    processRunner,
                    indexingService
                ),

                SimpleDownloadController(
                    cloud,
                    processRunner,
                    coreFileSystem,
                    bulkDownloadService,
                    tokenValidation
                ),

                MultiPartUploadController(
                    cloud,
                    processRunner,
                    coreFileSystem,
                    sensitivityService
                ),

                ExtractController(
                    cloud,
                    coreFileSystem,
                    fileLookupService,
                    processRunner,
                    sensitivityService
                ),

                WorkspaceController(workspaceService)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        BackgroundScope.stop()
    }
}
