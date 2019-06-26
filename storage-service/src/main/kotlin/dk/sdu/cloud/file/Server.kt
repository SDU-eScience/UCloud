package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingCallFilter
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.BackgroundJobController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.http.WorkspaceController
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.AuthUIDLookupService
import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.DevelopmentUIDLookupService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileScanner
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.WSFileSessionService
import dk.sdu.cloud.file.services.WorkspaceService
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclPermission
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.background.BackgroundExecutor
import dk.sdu.cloud.file.services.background.BackgroundJobHibernateDao
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.background.BackgroundStreams
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File

class Server(
    private val config: StorageConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)

        log.info("Creating core services")

        val bgDao = BackgroundJobHibernateDao()
        val bgExecutor =
            BackgroundExecutor(micro.hibernateDatabase, bgDao, BackgroundStreams("storage"), micro.eventStreamService)

        // Authentication
        val useFakeUsers = micro.developmentModeEnabled && !micro.commandLineArguments.contains("--real-users")
        val uidLookupService =
            if (useFakeUsers) DevelopmentUIDLookupService("admin@dev") else AuthUIDLookupService(client)

        // Authorization
        val homeFolderService = HomeFolderService(client)
        val aclDao = AclHibernateDao()
        val newAclService = AclService(micro.hibernateDatabase, aclDao, homeFolderService)

        run {
            // Authorization testing code. We should always add the user as the owner of their own home folder.
            // We simulate that this has already happened here.
            newAclService.createOrUpdatePermission("/home/admin@dev", "admin@dev", AclPermission.WRITE)
            newAclService.createOrUpdatePermission("/home/user@dev", "user@dev", AclPermission.WRITE)
        }

        // FS root
        val fsRootFile = File("/mnt/cephfs/").takeIf { it.exists() }
            ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")
        val fsRoot = fsRootFile.normalize().absolutePath

        // Low level FS
        val processRunner = LinuxFSRunnerFactory(uidLookupService)
        val fs = LinuxFS(fsRootFile, homeFolderService, newAclService)

        // High level FS
        val storageEventProducer = StorageEventProducer(streams.createProducer(StorageEvents.events)) {
            log.warn("Caught exception while emitting a storage event!")
            log.warn(it.stackTraceToString())
            stop()
        }

        // Metadata services
        val aclService = ACLService(processRunner, fs, bgExecutor).also { it.registerWorkers() }
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)

        // High level FS
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer, sensitivityService, client)

        // Bulk operations
        val bulkDownloadService = BulkDownloadService(coreFileSystem)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(coreFileSystem)
        val indexingService = IndexingService(processRunner, coreFileSystem, storageEventProducer)
        val fileScanner = FileScanner(processRunner, coreFileSystem, storageEventProducer)
        val workspaceService = WorkspaceService(fsRootFile, fileScanner, uidLookupService, processRunner)

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        // Initialize the background executor (must be last)
        bgExecutor.init()

        log.info("Core services constructed!")

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
                    client,
                    processRunner,
                    coreFileSystem,
                    bulkDownloadService,
                    tokenValidation
                ),

                MultiPartUploadController(
                    client,
                    processRunner,
                    coreFileSystem,
                    sensitivityService
                ),

                ExtractController(
                    client,
                    coreFileSystem,
                    fileLookupService,
                    processRunner,
                    sensitivityService
                ),

                WorkspaceController(workspaceService),

                BackgroundJobController(bgExecutor)
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        BackgroundScope.stop()
    }
}
