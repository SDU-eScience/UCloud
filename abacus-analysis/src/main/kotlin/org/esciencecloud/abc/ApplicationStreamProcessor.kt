package org.esciencecloud.abc

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.abc.processors.SlurmProcessor
import org.esciencecloud.abc.ssh.SSHConnectionPool
import org.esciencecloud.abc.ssh.SimpleSSHConfig
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
import kotlin.collections.set

data class HPCConfig(
        val kafka: KafkaConfiguration,
        val ssh: SimpleSSHConfig,
        val mail: MailAgentConfiguration,
        val storage: StorageConfiguration
)

data class StorageConfiguration(val host: String, val port: Int, val zone: String)
data class KafkaConfiguration(val servers: List<String>)

class ApplicationStreamProcessor(
        private val config: HPCConfig,
        private val storageConnectionFactory: StorageConnectionFactory,
        private val hostname: String,
        private val rpcPort: Int,
        private val mapper: ObjectMapper
) {
    companion object {
        const val TOPIC_HPC_APP_REQUESTS = "hpcAppRequests"
        const val TOPIC_HPC_APP_EVENTS = "hpcAppEvents"
        const val TOPIC_SLURM_TO_JOB_ID = "slurmIdToJobId"
        const val TOPIC_JOB_ID_TO_APP = "jobToApp"
    }

    private val log = LoggerFactory.getLogger(ApplicationStreamProcessor::class.java)

    private val sbatchGenerator = SBatchGenerator("sdu.esci.dev@gmail.com")
    private var initialized = false
    private lateinit var streamProcessor: KafkaStreams
    private lateinit var mailAgent: SlurmMailAgent

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
        if (initialized) throw IllegalStateException("Already started!")

        log.info("Starting SSH Connection Pool")
        val sshPool = SSHConnectionPool(config.ssh)

        // TODO How do we handle deserialization exceptions???
        log.info("Starting HPC Streams Processor")
        val hpcProcessor = HPCStreamProcessor(storageConnectionFactory, sbatchGenerator, config.ssh, sshPool)
        streamProcessor = KafkaStreams(
                KStreamBuilder().apply { hpcProcessor.constructStreams(this) },
                retrieveKafkaStreamsConfiguration()
        )
        streamProcessor.start()
        streamProcessor.addShutdownHook()

        log.info("Starting RPC Server")
        val rpc = HPCStoreEndpoints(hostname, rpcPort, streamProcessor)
        rpc.start()

        log.info("Starting Kafka Producer (Mail Agent)")
        val producer = KafkaProducer<String, String>(retrieveKafkaProducerConfiguration())

        log.info("Starting Slurm Mail Agent")
        mailAgent = SlurmMailAgent(config.mail)
        val slurmProcessor = SlurmProcessor(rpc, mapper, producer, sshPool, storageConnectionFactory)
        // TODO Handle this better. Can't do launch since we need it to block on agent side
        mailAgent.addListener { runBlocking { slurmProcessor.handle(it) } }
        mailAgent.start()

        log.info("Ready!")
        initialized = true
    }

    fun stop() {
        mailAgent.stop()
        streamProcessor.close()
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

    val processor = ApplicationStreamProcessor(hpcConfig, irodsConnectionFactory, "localhost", 42200, mapper)
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

