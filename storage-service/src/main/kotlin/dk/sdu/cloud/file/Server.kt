package dk.sdu.cloud.file

import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.authenticator
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingCallFilter
import dk.sdu.cloud.calls.server.securityPrincipal
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.http.ExtractController
import dk.sdu.cloud.file.http.FilesController
import dk.sdu.cloud.file.http.IndexingController
import dk.sdu.cloud.file.http.MultiPartUploadController
import dk.sdu.cloud.file.http.SimpleDownloadController
import dk.sdu.cloud.file.processors.UserProcessor
import dk.sdu.cloud.file.services.ACLService
import dk.sdu.cloud.file.services.AuthUIDLookupService
import dk.sdu.cloud.file.services.BackgroundScope
import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.DevelopmentUIDLookupService
import dk.sdu.cloud.file.services.FileAnnotationService
import dk.sdu.cloud.file.services.FileLookupService
import dk.sdu.cloud.file.services.FileScanner
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.HomeFolderService
import dk.sdu.cloud.file.services.IndexingService
import dk.sdu.cloud.file.services.unixfs.FileAttributeParser
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import dk.sdu.cloud.kafka.forStream
import dk.sdu.cloud.kafka.stream
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.developmentModeEnabled
import dk.sdu.cloud.micro.kafka
import dk.sdu.cloud.micro.server
import dk.sdu.cloud.micro.tokenValidation
import dk.sdu.cloud.service.CommonServer
import dk.sdu.cloud.service.EventConsumer
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.WithKafkaStreams
import dk.sdu.cloud.service.buildStreams
import dk.sdu.cloud.service.configureControllers
import dk.sdu.cloud.service.installShutdownHandler
import dk.sdu.cloud.service.stackTraceToString
import dk.sdu.cloud.service.startServices
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    private val config: StorageConfiguration,
    override val micro: Micro
) : CommonServer, WithKafkaStreams {
    override val log: Logger = logger()
    override lateinit var kStreams: KafkaStreams

    private val allProcessors = ArrayList<EventConsumer<*>>()

    private fun addProcessors(processors: List<EventConsumer<*>>) {
        processors.forEach { it.installShutdownHandler(this) }
        allProcessors.addAll(processors)
    }

    override fun start() {
        val kafka = micro.kafka
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
        val processRunner = UnixFSCommandRunnerFactory(uidLookupService)
        val fileAttributeParser = FileAttributeParser(uidLookupService)
        val fs = UnixFileSystem(processRunner, uidLookupService, fileAttributeParser, fsRoot)

        // High level FS
        val storageEventProducer = kafka.producer.forStream(StorageEvents.events)
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer)

        // Bulk operations
        val bulkDownloadService = BulkDownloadService(coreFileSystem)

        // Specialized operations (built on high level FS)
        val fileLookupService = FileLookupService(coreFileSystem)
        val indexingService = IndexingService(processRunner, coreFileSystem, storageEventProducer)
        val fileScanner = FileScanner(processRunner, coreFileSystem, storageEventProducer)

        // Metadata services
        val aclService = ACLService(fs)
        val annotationService = FileAnnotationService(fs, storageEventProducer)
        val sensitivityService = FileSensitivityService(fs, storageEventProducer)
        val homeFolderService = HomeFolderService(cloud)

        coreFileSystem.setOnStorageEventExceptionHandler {
            log.warn("Caught exception while emitting a storage event!")
            log.warn(it.stackTraceToString())
            stop()
        }

        log.info("Core services constructed!")

        kStreams = buildStreams { kBuilder ->
            UserProcessor(
                kBuilder.stream(AuthStreams.UserUpdateStream),
                uidLookupService,
                fileScanner,
                processRunner,
                coreFileSystem
            ).init()
        }

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
                FilesController(
                    processRunner,
                    coreFileSystem,
                    annotationService,
                    fileLookupService,
                    sensitivityService,
                    aclService,
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
                    cloud,
                    processRunner,
                    coreFileSystem,
                    sensitivityService
                ),

                ExtractController(
                    cloud,
                    coreFileSystem,
                    fileLookupService,
                    processRunner
                )
            )
        }

        startServices()
    }

    override fun stop() {
        super.stop()
        allProcessors.forEach { it.close() }
        BackgroundScope.stop()
    }
}
