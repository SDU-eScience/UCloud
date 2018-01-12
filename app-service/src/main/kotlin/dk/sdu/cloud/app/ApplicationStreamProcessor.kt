package dk.sdu.cloud.app

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.app.api.AppServiceDescription
import dk.sdu.cloud.app.http.AppController
import dk.sdu.cloud.app.http.JobController
import dk.sdu.cloud.app.http.ToolController
import dk.sdu.cloud.app.processors.SlurmAggregate
import dk.sdu.cloud.app.processors.SlurmProcessor
import dk.sdu.cloud.app.processors.StartProcessor
import dk.sdu.cloud.app.services.*
import dk.sdu.cloud.app.services.ssh.SSHConnectionPool
import dk.sdu.cloud.app.services.ssh.SimpleSSHConfig
import dk.sdu.cloud.auth.api.JWTProtection
import dk.sdu.cloud.auth.api.RefreshingJWTAuthenticator
import dk.sdu.cloud.auth.api.protect
import dk.sdu.cloud.service.*
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaProducerConfiguration
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaStreamsConfiguration
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import io.ktor.application.install
import io.ktor.routing.route
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class HPCConfig(
        val connection: RawConnectionConfig,
        val ssh: SimpleSSHConfig,
        val storage: StorageConfiguration,
        val rpc: RPCConfiguration,
        val refreshToken: String,
        val database: DatabaseConfiguration
)

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class RPCConfiguration(val secretToken: String)

class ApplicationStreamProcessor(
        private val config: HPCConfig,
        private val storageConnectionFactory: StorageConnectionFactory
) {
    private val log = LoggerFactory.getLogger(ApplicationStreamProcessor::class.java)

    private var initialized = false
    private lateinit var rpcServer: HTTPServer
    private lateinit var streamProcessor: KafkaStreams
    private lateinit var scheduledExecutor: ScheduledExecutorService
    private lateinit var slurmPollAgent: SlurmPollAgent

    fun start() {
        // TODO How do we handle deserialization exceptions???
        // TODO Create some component interfaces
        // TODO This would most likely be a lot better if we could use DI in this
        if (initialized) throw IllegalStateException("Already started!")

        val connConfig = config.connection.processed
        val instance = AppServiceDescription.instance(connConfig)

        val (zk, node) = runBlocking {
            val zk = ZooKeeperConnection(connConfig.zookeeper.servers).connect()
            val node = zk.registerService(instance)

            Pair(zk, node)
        }

        log.info("Init Core Services")
        val streamBuilder = StreamsBuilder()
        val producer = KafkaProducer<String, String>(retrieveKafkaProducerConfiguration(connConfig))
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        val cloud = RefreshingJWTAuthenticator(DirectServiceClient(zk), config.refreshToken)
        Database.connect(
                url = config.database.url,
                driver = config.database.driver,

                user = config.database.username,
                password = config.database.password
        )



        log.info("Init Application Services")
        val sshPool = SSHConnectionPool(config.ssh)

        val streamService = HPCStreamService(streamBuilder, producer)
        val sbatchGenerator = SBatchGenerator()

        if (false) {
            streamService.appEvents.foreach { key, value ->  }
            streamService.appRequests.unauthenticated.foreach { _, _ -> }
            streamService.appRequests.authenticated.foreach { _, _ -> }
        }

        if (false) {
            transaction {
                create(JobsTable, JobStatusTable)
            }
            println("OK")
            return
        }

        slurmPollAgent = SlurmPollAgent(sshPool, scheduledExecutor, 0L, 15L, TimeUnit.SECONDS)

        log.info("Init Event Processors")
        val slurmProcessor = SlurmProcessor(cloud, sshPool, storageConnectionFactory, slurmPollAgent, streamService)
        val slurmAggregate = SlurmAggregate(streamService, slurmPollAgent)
        val startProcessor = StartProcessor(storageConnectionFactory, sbatchGenerator, sshPool, config.ssh.user,
                streamService)

        log.info("Starting Event Processors")
        slurmProcessor.init()
        slurmAggregate.init()
        startProcessor.init()

        log.info("Starting Application Services")
        slurmPollAgent.start()

        log.info("Starting Core Services")
        streamProcessor = KafkaStreams(streamBuilder.build(), retrieveKafkaStreamsConfiguration(connConfig))
        streamProcessor.start()
        streamProcessor.addShutdownHook()

        log.info("Starting HTTP Server")
        rpcServer = HTTPServer(connConfig.service.hostname, connConfig.service.port)
        rpcServer.start {
            install(JWTProtection)

            route("api") {
                route("hpc") {
                    protect()

                    AppController(ApplicationDAO).configure(this)
                    JobController().configure(this)
                    ToolController(ToolDAO).configure(this)
                }
            }
        }

        log.info("Ready!")
        initialized = true

        runBlocking { zk.markServiceAsReady(node, instance) }
    }

    fun stop() {
        rpcServer.stop()
        slurmPollAgent.stop()
        streamProcessor.close()
        scheduledExecutor.shutdown()
    }

    private fun KafkaStreams.addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread { this.close() })
    }
}

fun <T : Any> Error.Companion.internalError(): Error<T> = Error(500, "Internal error")
fun Exception.stackTraceToString(): String = StringWriter().apply { printStackTrace(PrintWriter(this)) }.toString()

fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()
    val hpcConfig = mapper.readValue<HPCConfig>(File("hpc_conf.json"))
    hpcConfig.connection.configure(AppServiceDescription, 42200)

    val irodsConnectionFactory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = hpcConfig.storage.host,
            zone = hpcConfig.storage.zone,
            port = hpcConfig.storage.port,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REQUIRE,
            authScheme = AuthScheme.PAM
    ))

    val processor = ApplicationStreamProcessor(hpcConfig, irodsConnectionFactory)
    processor.start()
}

