package dk.sdu.cloud.tus

import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticatedCloud
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.ext.irods.ICAT
import dk.sdu.cloud.tus.api.TusHeaders
import dk.sdu.cloud.tus.api.TusServiceDescription
import dk.sdu.cloud.tus.http.TusController
import dk.sdu.cloud.tus.services.*
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class Server(
    private val configuration: Configuration,
    private val kafka: KafkaServices,
    private val serviceRegistry: ServiceRegistry,
    private val ktor: HttpServerProvider,
    private val cloud: RefreshingJWTAuthenticatedCloud,
    private val args: Array<String>
) {
    private lateinit var httpServer: ApplicationEngine
    private lateinit var kStreams: KafkaStreams

    fun start() {
        val instance = TusServiceDescription.instance(configuration.connConfig)

        log.info("Creating core services")
        val cephStore = CephStore("client.irods", File("ceph.conf"), "irods")
        val downloadService = DownloadService(cephStore)
        val checksumService = ChecksumService(downloadService, cephStore)
        val transferState = TusStateService()
        val icat = ICAT(configuration.database)
        val tus = TusController(
            config = configuration.database,
            store = cephStore,
            tusState = transferState,
            icat = icat,
            checksumService = checksumService
        )
        log.info("Core services constructed!")

        // Scripts
        val checksumIdx = args.indexOfFirst { it == "--checksum" }
        if (checksumIdx != -1) {
            val objectId = args[checksumIdx + 1]
            val (checksum, fileSize) = runBlocking {
                checksumService.computeChecksumAndFileSize(objectId)
            }

            log.info("Checksum: ${checksum.toHexString()}")
            log.info("File size: $fileSize")
            return
        }

        val checksumFileIdx = args.indexOfFirst { it == "--checksum-file" }
        if (checksumFileIdx != -1) {
            val file = File(args[checksumFileIdx + 1])
            val objectIds = file.readLines()
            runBlocking {
                objectIds.map { oid ->
                    launch {
                        val (checksum, fileSize) = checksumService.computeChecksumAndFileSize(oid)
                        checksumService.attachChecksumToObject(oid, checksum)
                        checksumService.attachFilesizeToObject(oid, fileSize)

                        log.info("$oid: $fileSize ${checksum.toHexString()}")
                    }
                }.forEach { it.join() }
            }
            return
        }

        val readChecksums = args.indexOfFirst { it == "--read-checksum-file" }
        if (readChecksums != -1) {
            val file = File(args[readChecksums + 1])
            val objectIds = file.readLines()
            runBlocking {
                objectIds.map { oid ->
                    launch {
                        val checksum = checksumService.getChecksum(oid)
                        val fileSize = checksumService.getFileSize(oid)
                        log.info("$oid: $fileSize $checksum")
                    }
                }.forEach { it.join() }
            }
            return
        }
        // End of scripts

        kStreams = run {
            log.info("Constructing Kafka Streams Topology")
            val kBuilder = StreamsBuilder()
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
                allowCredentials = false
            }

            routing {
                route("api/tus") {
                    tus.registerTusEndpoint(this, "/api/tus")
                }
            }

            log.info("HTTP server successfully configured!")
        }

        log.info("Starting HTTP server...")
        httpServer.start(wait = false)
        log.info("HTTP server started!")

        log.info("Starting Kafka Streams...")
        kStreams.start()
        log.info("Kafka Streams started!")

        serviceRegistry.register(listOf("/api/tus"))
        log.info("Server is ready!")
        log.info(instance.toString())
    }

    fun stop() {
        httpServer.stop(gracePeriod = 0, timeout = 30, timeUnit = TimeUnit.SECONDS)
        kStreams.close(30, TimeUnit.SECONDS)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }
}
