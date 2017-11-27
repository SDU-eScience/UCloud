package org.esciencecloud.abc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.routing.route
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.abc.http.AppController
import org.esciencecloud.abc.http.JobController
import org.esciencecloud.abc.http.ToolController
import org.esciencecloud.abc.processors.SlurmAggregate
import org.esciencecloud.abc.processors.SlurmProcessor
import org.esciencecloud.abc.processors.StartProcessor
import org.esciencecloud.abc.services.*
import org.esciencecloud.abc.services.ssh.SSHConnectionPool
import org.esciencecloud.abc.services.ssh.SimpleSSHConfig
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.collections.set

data class HPCConfig(
        val kafka: KafkaConfiguration,
        val ssh: SimpleSSHConfig,
        val mail: MailAgentConfiguration,
        val storage: StorageConfiguration,
        val rpc: RPCConfiguration
)

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class KafkaConfiguration(val servers: List<String>)
data class RPCConfiguration(val secretToken: String)

class ApplicationStreamProcessor(
        private val config: HPCConfig,
        private val storageConnectionFactory: StorageConnectionFactory,
        private val hostname: String,
        private val rpcPort: Int
) {
    private val log = LoggerFactory.getLogger(ApplicationStreamProcessor::class.java)

    private var initialized = false
    private lateinit var rpcServer: HTTPServer
    private lateinit var streamProcessor: KafkaStreams
    private lateinit var scheduledExecutor: ScheduledExecutorService
    private lateinit var slurmPollAgent: SlurmPollAgent

    private fun retrieveKafkaStreamsConfiguration(): Properties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.servers.joinToString(",")
        this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        this[StreamsConfig.APPLICATION_SERVER_CONFIG] = "$hostname:$rpcPort"
    }

    private fun retrieveKafkaProducerConfiguration(): Properties = Properties().apply {
        this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafka.servers.joinToString(",")
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
    }

    fun start() {
        // TODO How do we handle deserialization exceptions???
        // TODO Create some component interfaces
        // TODO This would most likely be a lot better if we could use DI in this
        if (initialized) throw IllegalStateException("Already started!")

        log.info("Init Core Services")
        val streamBuilder = KStreamBuilder()
        val producer = KafkaProducer<String, String>(retrieveKafkaProducerConfiguration())
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

        log.info("Init Application Services")
        val sshPool = SSHConnectionPool(config.ssh)
        val hpcStore = HPCStore(hostname, rpcPort, config.rpc)
        val streamService = HPCStreamService(storageConnectionFactory, streamBuilder, producer)
        val sbatchGenerator = SBatchGenerator("sdu.esci.dev@gmail.com")
        slurmPollAgent = SlurmPollAgent(sshPool, scheduledExecutor, 0L, 15L, TimeUnit.SECONDS)

        log.info("Init Event Processors")
        val slurmProcessor = SlurmProcessor(hpcStore, sshPool, storageConnectionFactory, slurmPollAgent, streamService)
        val slurmAggregate = SlurmAggregate(streamService, slurmPollAgent)
        val startProcessor = StartProcessor(sbatchGenerator, sshPool, config.ssh.user, streamService)

        log.info("Starting Event Processors")
        slurmProcessor.init()
        slurmAggregate.init()
        startProcessor.init()

        log.info("Starting Application Services")
        slurmPollAgent.start()

        log.info("Starting Core Services")
        streamProcessor = KafkaStreams(streamBuilder, retrieveKafkaStreamsConfiguration())
        streamProcessor.start()
        streamProcessor.addShutdownHook()

        log.info("Starting HTTP Server")
        rpcServer = HTTPServer(hostname, rpcPort)
        rpcServer.start {
            hpcStore.init(streamProcessor, this)

            route("api") {
                AppController(ApplicationDAO).configure(this)
                JobController(hpcStore).configure(this)
                ToolController(ToolDAO).configure(this)
            }
        }

        log.info("Ready!")
        initialized = true
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

    val irodsConnectionFactory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = hpcConfig.storage.host,
            zone = hpcConfig.storage.zone,
            port = hpcConfig.storage.port,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
            authScheme = AuthScheme.STANDARD
    ))

    val processor = ApplicationStreamProcessor(hpcConfig, irodsConnectionFactory, "localhost", 42200)
    processor.start()
}

// TODO Should be shared!
data class Request<out EventType>(val header: RequestHeader, val event: EventType) {
    companion object {
        const val TYPE_PROPERTY = "type"
    }
}

data class RequestHeader(
        val uuid: String,
        val performedFor: ProxyClient
)

data class ProxyClient(val username: String, val password: String)
