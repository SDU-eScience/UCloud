package dk.sdu.cloud.storage

import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.storage.http.*
import dk.sdu.cloud.storage.processor.ChecksumProcessor
import dk.sdu.cloud.storage.processor.StorageEventProcessor
import dk.sdu.cloud.storage.processor.UserProcessor
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import dk.sdu.cloud.tus.api.TusHeaders
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.Logger
import java.io.File

class Server(
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val instance: ServiceInstance,
    private val args: Array<String>
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
        val isDevelopment = args.contains("--dev")

        val cloudToCephFsDao = CephFSUserDao(isDevelopment)
        val processRunner = CephFSCommandRunnerFactory(cloudToCephFsDao, isDevelopment)
        val fsRoot = File(if (isDevelopment) "./fs/" else "/mnt/cephfs/").normalize().absolutePath

        val fs = CephFileSystem(cloudToCephFsDao, fsRoot)
        val storageEventProducer = kafka.producer.forStream(StorageEvents.events)
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer)

        val aclService = ACLService(fs)

        val annotationService = FileAnnotationService(fs, storageEventProducer)

        val favoriteService = FavoriteService(coreFileSystem)
        val uploadService = BulkUploadService(coreFileSystem)
        val bulkDownloadService = BulkDownloadService(coreFileSystem)
        val transferState = TusHibernateDAO()
        val fileLookupService = FileLookupService(coreFileSystem, favoriteService)

        val indexingService = IndexingService(processRunner, coreFileSystem, storageEventProducer)

        val shareDAO = ShareHibernateDAO()
        val shareService = ShareService(db, shareDAO, processRunner, aclService, coreFileSystem)

        val externalFileService = ExternalFileService(processRunner, coreFileSystem, storageEventProducer)
        log.info("Core services constructed!")

        // Kafka
        kStreams = buildStreams { kBuilder ->
            UserProcessor(
                kBuilder.stream(AuthStreams.UserUpdateStream),
                isDevelopment,
                cloudToCephFsDao,
                externalFileService
            ).init()
        }

        val storageEventProcessor = StorageEventProcessor(kafka)
        addProcessors(storageEventProcessor.init())

        ChecksumProcessor(processRunner, fs, coreFileSystem).also {
            // TODO Doesn't emit events for checksums
            storageEventProcessor.registerHandler(it::handleEvents)
        }

        // HTTP
        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(CORS) {
                anyHost()
                header(HttpHeaders.Authorization)
                header("Job-Id")
                header(TusHeaders.Extension)
                header(TusHeaders.MaxSize)
                header(TusHeaders.Resumable)
                header(TusHeaders.UploadLength)
                header(TusHeaders.UploadOffset)
                header(TusHeaders.Version)
                header("upload-metadata")

                exposeHeader(HttpHeaders.Location)
                exposeHeader(TusHeaders.Extension)
                exposeHeader(TusHeaders.MaxSize)
                exposeHeader(TusHeaders.Resumable)
                exposeHeader(TusHeaders.UploadLength)
                exposeHeader(TusHeaders.UploadOffset)
                exposeHeader(TusHeaders.Version)

                HttpMethod.DefaultMethods.forEach { method(it) }
                allowCredentials = false
            }

            routing {
                route("api/tus") {
                    val tus = TusController(db, transferState, processRunner, coreFileSystem)
                    tus.registerTusEndpoint(this, "/api/tus")
                }

                configureControllers(
                    FilesController(
                        processRunner,
                        coreFileSystem,
                        annotationService,
                        favoriteService,
                        fileLookupService
                    ),

                    IndexingController(
                        processRunner,
                        indexingService
                    ),

                    SimpleDownloadController(
                        cloud,
                        processRunner,
                        coreFileSystem,
                        bulkDownloadService
                    ),

                    MultiPartUploadController(
                        processRunner,
                        coreFileSystem,
                        uploadService
                    ),

                    ShareController(
                        shareService,
                        processRunner
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
    }
}
