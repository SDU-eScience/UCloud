package dk.sdu.cloud.storage

import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.db.HibernateSessionFactory
import dk.sdu.cloud.storage.api.StorageEvents
import dk.sdu.cloud.storage.api.StorageServiceDescription
import dk.sdu.cloud.storage.api.TusHeaders
import dk.sdu.cloud.storage.http.*
import dk.sdu.cloud.storage.processor.UserProcessor
import dk.sdu.cloud.storage.services.*
import dk.sdu.cloud.storage.services.cephfs.*
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.launch
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class Server(
    private val configuration: Configuration,
    override val kafka: KafkaServices,
    private val ktor: HttpServerProvider,
    private val db: HibernateSessionFactory,
    override val serviceRegistry: ServiceRegistry,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>
): CommonServer, WithServiceRegistry {
    override val log: Logger = logger()
    override val endpoints = listOf("/api/tus", "/api/files", "/api/shares")

    override lateinit var httpServer: ApplicationEngine
    override lateinit var kStreams: KafkaStreams

    override fun start() {
        val instance = StorageServiceDescription.instance(configuration.connConfig)

        log.info("Creating core services")
        val isDevelopment = args.contains("--dev")

        val cloudToCephFsDao = CephFSUserDao(isDevelopment)
        val processRunner = CephFSCommandRunnerFactory(cloudToCephFsDao, isDevelopment)
        val fsRoot = File(if (isDevelopment) "./fs/" else "/mnt/cephfs/").normalize().absolutePath

        val fs = CephFileSystem(cloudToCephFsDao, fsRoot)
        val storageEventProducer = kafka.producer.forStream(StorageEvents.events)
        val coreFileSystem = CoreFileSystemService(fs, storageEventProducer)

        val aclService = ACLService(fs)

        // TODO Breaks previous contract that CoreFS would emit all events
        val annotationService = FileAnnotationService(fs, storageEventProducer)

        val checksumService = ChecksumService(processRunner, fs, coreFileSystem).also {
            // TODO Will only receive events from CoreFS, not from others.
            // TODO Doesn't emit events for checksums
            launch { it.attachToFSChannel(coreFileSystem.openEventSubscription()) }
        }
        val favoriteService = FavoriteService(coreFileSystem)
        val uploadService = BulkUploadService(coreFileSystem)
        val bulkDownloadService = BulkDownloadService(coreFileSystem)
        val transferState = TusHibernateDAO()
        val fileLookupService = FileLookupService(coreFileSystem, favoriteService)

        // TODO Breaks previous contract that CoreFS would emit all events
        val indexingService = IndexingService(processRunner, coreFileSystem, storageEventProducer)

        val shareDAO = ShareHibernateDAO()
        val shareService = ShareService(db, shareDAO, processRunner, aclService, coreFileSystem)
        log.info("Core services constructed!")

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()

            log.info("Configuring stream processors...")
            UserProcessor(kBuilder.stream(AuthStreams.UserUpdateStream), isDevelopment, cloudToCephFsDao).init()
            log.info("Stream processors configured!")

            kafka.build(kBuilder.build()).also {
                log.info("Kafka Streams Topology successfully built!")
            }
        }

        kStreams.setUncaughtExceptionHandler { _, exception ->
            log.error("Caught fatal exception in Kafka! Stacktrace follows:")
            log.error(exception.stackTraceToString())
            stop()
        }

        httpServer = ktor {
            log.info("Configuring HTTP server")
            installDefaultFeatures(cloud, kafka, instance, requireJobId = false)
            install(JWTProtection)
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

                method(HttpMethod.Patch)
                method(HttpMethod.Options)
                method(HttpMethod.Delete)
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
                        processRunner,
                        coreFileSystem
                    )
                )
            }
            log.info("HTTP server successfully configured!")
        }

        startServices()
        registerWithRegistry()
    }
}
