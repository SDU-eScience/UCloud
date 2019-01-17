package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.FilesController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.processors.StorageEventProcessor
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.ExternalFileService
import dk.sdu.cloud.file.services.FavoriteService
import dk.sdu.cloud.file.services.FileAnnotationService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileOwnerService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFSUserDao
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.HttpServerProvider
import dk.sdu.cloud.service.KafkaServices
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.developmentModeEnabled
import dk.sdu.cloud.service.forStream
import dk.sdu.cloud.service.installDefaultFeatures
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.startServices
import dk.sdu.cloud.service.stream
import dk.sdu.cloud.service.tokenValidation
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val config: StorageConfiguration,
    private val micro: Micro
) : CommonServer {
    override val log: Logger = logger()

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    private val allProcessors = ArrayList<EventConsumer<*>>()

    private fun addProcessors(processors: List<EventConsumer<*>>) {
        processors.forEach { it.installShutdownHandler(this) }
        allProcessors.addAll(processors)
    }

    override fun start() {
        log.info("Creating core services")
        BackgroundScope.init()
        val useFakeUsers = micro.developmentModeEnabled && !micro.commandLineArguments.contains("--real-users")
        val cloudToCephFsDao = UnixFSUserDao(useFakeUsers)
        val processRunner =
            UnixFSCommandRunnerFactory(cloudToCephFsDao, useFakeUsers)
        val fsRootFile = File("/mnt/cephfs/").takeIf { it.exists() } ?:
            if (micro.developmentModeEnabled) File("./fs") else throw IllegalStateException("No mount found!")

        val fsRoot = fsRootFile.normalize().absolutePath

        val fs = UnixFileSystem(cloudToCephFsDao, fsRoot)
        val storageEventProducer = kafka.producer.forStream(StorageEvents.events)
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer)

        val aclService = ACLService(fs)

        val annotationService = FileAnnotationService(fs, storageEventProducer)

        val favoriteService = FavoriteService(coreFileSystem)
        val bulkDownloadService = BulkDownloadService(coreFileSystem)
        val fileLookupService = FileLookupService(coreFileSystem, favoriteService)

        val indexingService =
            IndexingService(processRunner, coreFileSystem, storageEventProducer)

        val sensitivityService = FileSensitivityService(fs, storageEventProducer)

        val externalFileService =
            ExternalFileService(processRunner, coreFileSystem, storageEventProducer)

        val fileOwnerService = FileOwnerService(processRunner, fs, coreFileSystem)

        val homeFolderService = HomeFolderService(cloud)

        log.info("Core services constructed!")

        // Kafka
        addProcessors(
            StorageEventProcessor(
                listeners = listOf(
                    fileOwnerService
                ),
                commandRunnerFactory = processRunner,
                eventConsumerFactory = kafka
            ).init()
        )

        kStreams = buildStreams { kBuilder ->
            UserProcessor(
                kBuilder.stream(AuthStreams.UserUpdateStream),
                micro.developmentModeEnabled,
                cloudToCephFsDao,
                externalFileService,
                processRunner,
                coreFileSystem
            ).init()
        }

        val tokenValidation =
            micro.tokenValidation as? TokenValidationJWT ?: throw IllegalStateException("JWT token validation required")

        // HTTP
        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(micro)

            routing {
                configureControllers(
                    FilesController(
                        processRunner,
                        coreFileSystem,
                        annotationService,
                        favoriteService,
                        fileLookupService,
                        sensitivityService,
                        aclService,
                        fileOwnerService,
                        homeFolderService,
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
                        processRunner,
                        coreFileSystem,
                        sensitivityService
                    ),

                    MultiPartUploadController(
                        processRunner,
                        coreFileSystem,
                        sensitivityService,
                        baseContextOverride = "/api/upload" // backwards-comparability
                    )
                )
            }
            log.info("HTTP server successfully configured!")
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        allProcessors.forEach { it.close() }
        BackgroundScope.stop()
    }
}
