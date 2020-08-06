package dk.sdu.cloud.file

import com.sun.jna.Platform
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.client.OutgoingWSCall
import dk.sdu.cloud.file.http.ActionController
import dk.sdu.cloud.file.http.CommandRunnerFactoryForCalls
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FileSecurityController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.LookupController
import dk.sdu.cloud.file.http.MetadataController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.*
import dk.sdu.cloud.file.services.acl.AclService
import dk.sdu.cloud.file.services.acl.MetadataDao
import dk.sdu.cloud.file.services.acl.MetadataService
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.backgroundScope
import dk.sdu.cloud.micro.databaseConfig
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.eventStreamService
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.DistributedLockBestEffortFactory
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.file.processors.ProjectProcessor
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.io.File

class Server(
    private val config: StorageConfiguration,
    private val cephConfig: CephConfiguration,
    override val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override fun start() = runBlocking {
        val streams = micro.eventStreamService
        val client = micro.authenticator.authenticateClient(OutgoingHttpCall)
        val wsClient = micro.authenticator.authenticateClient(OutgoingWSCall)

        log.info("Creating core services")

        require(Platform.isLinux() || micro.developmentModeEnabled) {
            "This service is only able to run on GNU/Linux in production mode"
        }

        // FS root
        val fsRootFile =
            File((cephConfig.cephfsBaseMount ?: "/mnt/cephfs/") + cephConfig.subfolder).takeIf { it.exists() }
                ?: if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        log.info("Serving files from ${fsRootFile.absolutePath}")

        val homeFolderService = HomeFolderService()
        val db = AsyncDBSessionFactory(micro.databaseConfig)
        val metadataDao = MetadataDao()
        val metadataService = MetadataService(db, metadataDao)
        val projectCache = ProjectCache(client)
        val newAclService = AclService(metadataService, homeFolderService, client, projectCache)

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

        log.info("Core services constructed!")

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
                    fileLookupService
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

                MetadataController(metadataService, metadataRecovery)
            )
        }

        startServices()
    }
}
