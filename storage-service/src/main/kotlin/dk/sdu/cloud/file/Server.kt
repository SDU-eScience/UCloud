package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.WorkspaceMode
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.http.WorkspaceController
import dk.sdu.cloud.file.processors.ScanProcessor
import dk.sdu.cloud.file.processors.StorageProcessor
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.ACLWorker
import dk.sdu.cloud.file.services.CopyFilesWorkspaceCreator
import dk.sdu.cloud.file.services.CopyOnWriteWorkspaceCreator
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileScanner
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.WSFileSessionService
import dk.sdu.cloud.file.services.WorkspaceService
import dk.sdu.cloud.file.services.acl.AclHibernateDao
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.linuxfs.Chown
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.linuxfs.LinuxFSScope
import dk.sdu.cloud.file.services.linuxfs.linuxFSRealPathSupplier
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.hibernateDatabase
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.H2_DIALECT
import dk.sdu.cloud.service.db.H2_DRIVER
import dk.sdu.cloud.service.db.withTransaction
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File
import kotlin.system.exitProcess

class Server(
    private val config: StorageConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        supportReverseInH2(micro)

        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        log.info("Creating core services")

        Chown.isDevMode = micro.developmentModeEnabled

        // FS root
        val fsRootFile = File("/mnt/cephfs/").takeIf { it.exists() }
            ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        // Authorization
        val homeFolderService = HomeFolderService(client)
        val aclDao = AclHibernateDao()
        val newAclService =
            AclService(micro.hibernateDatabase, aclDao, homeFolderService, linuxFSRealPathSupplier())

        // Low level FS
        val processRunner = LinuxFSRunnerFactory(micro.backgroundScope)
        val fs = LinuxFS(fsRootFile, newAclService)

        // High level FS
        val storageEventProducer =
            StorageEventProducer(streams.createProducer(StorageEvents.events), micro.backgroundScope) {
                log.warn("Caught exception while emitting a storage event!")
                log.warn(it.stackTraceToString())
                stop()
            }

        // Metadata services
        val aclService = ACLWorker(newAclService)
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)

        // High level FS
        val coreFileSystem =
            CoreFileSystemService(fs, storageEventProducer, sensitivityService, wsClient, micro.backgroundScope)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(processRunner, coreFileSystem)
        val indexingService = IndexingService(
            processRunner,
            coreFileSystem,
            storageEventProducer,
            newAclService,
            micro.eventStreamService
        )
        val fileScanner = FileScanner(processRunner, coreFileSystem, storageEventProducer, micro.backgroundScope)
        val workspaceService = WorkspaceService(fsRootFile, mapOf(
            WorkspaceMode.COPY_FILES to CopyFilesWorkspaceCreator(
                fsRootFile.absoluteFile.normalize(),
                fileScanner,
                newAclService,
                coreFileSystem,
                processRunner
            ),

            WorkspaceMode.COPY_ON_WRITE to CopyOnWriteWorkspaceCreator(
                fsRootFile.absoluteFile.normalize(),
                newAclService,
                processRunner,
                coreFileSystem
            )
        ))

        // RPC services
        val wsService = WSFileSessionService(processRunner)
        val commandRunnerForCalls = CommandRunnerFactoryForCalls(processRunner, wsService)

        log.info("Core services constructed!")

        if (micro.commandLineArguments.contains("--scan")) {
            val index = micro.commandLineArguments.indexOf("--scan")
            if (micro.commandLineArguments.size > index+1) {
                val path = micro.commandLineArguments[index + 1]
                if (!path.startsWith("/")){
                    log.info("Must give path as argument after --scan")
                    exitProcess(1)
                }
                fileScanner.scanFilesCreatedExternally(path)
                exitProcess(0)
            }
            log.info("Missing argument after --scan")
            exitProcess(1)
        }

        UserProcessor(
            streams,
            fileScanner
        ).init()

        StorageProcessor(streams, newAclService).init()

        ScanProcessor(streams, indexingService).init()

        val tokenValidation =
            micro.tokenValidation as? TokenValidationJWT ?: throw IllegalStateException("JWT token validation required")

        // HTTP
        with(micro.server) {
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
                    commandRunnerForCalls,
                    coreFileSystem,
                    tokenValidation,
                    fileLookupService
                ),

                MultiPartUploadController(
                    client,
                    commandRunnerForCalls,
                    coreFileSystem,
                    sensitivityService,
                    micro.backgroundScope
                ),

                ExtractController(
                    client,
                    coreFileSystem,
                    fileLookupService,
                    commandRunnerForCalls,
                    sensitivityService,
                    micro.backgroundScope
                ),

                WorkspaceController(workspaceService)
            )
        }

        startServices()
    }

    private suspend fun supportReverseInH2(micro: Micro) {
        val databaseConfig = micro.databaseConfig

        if (databaseConfig.dialect == H2_DIALECT || databaseConfig.driver == H2_DRIVER) {
            // Add database 'polyfill' for postgres reverse function.
            log.info("Adding the H2 polyfill")
            micro.hibernateDatabase.withTransaction { session ->
                session.createNativeQuery(
                    "CREATE ALIAS IF NOT EXISTS REVERSE AS \$\$ " +
                            "String reverse(String s) { return new StringBuilder(s).reverse().toString(); } " +
                            "\$\$;"
                ).executeUpdate()
            }
        }

        micro.hibernateDatabase.withTransaction { session ->
            try {
                session.createNativeQuery("select REVERSE('foo')").list().first().toString()
            } catch (ex: Throwable) {
                log.error("Could not reverse string in database!")
                exitProcess(1)
            }
        }
    }

    override fun stop() {
        super.stop()
        LinuxFSScope.close()
    }
}
