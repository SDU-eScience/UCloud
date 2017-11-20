package org.esciencecloud.abc

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import java.util.*

data class KafkaConfiguration(val servers: List<String>)

fun retrieveKafkaConfiguration(kafkaConfig: KafkaConfiguration): Properties {
    val properties = Properties()
    properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
    properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers.joinToString(",")
    properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
    return properties
}

fun constructStreams(builder: KStreamBuilder) {
    val irods = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = "localhost",
            zone = "tempZone",
            port = 1247,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
            authScheme = AuthScheme.STANDARD
    ))

    val stream = builder.stream<String, Request<ABCApplicationEvent>>(jsonSerde(), jsonSerde(), "abcApplicationEvents")
    stream.map { key, request ->
        val connection = validateAndConnectToStorage(request.header)

        when (request.event) {
            is ABCApplicationEvent.Start -> {
                request.event.application
            }

            is ABCApplicationEvent.Cancel -> {
            }
        }

        KeyValue("", 123L)
    }
}

private fun validateAndConnectToStorage(requestHeader: RequestHeader): StorageConnection {
    TODO()
}

private fun KafkaStreams.addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread { this.close() })
}

fun main(args: Array<String>) {
    /*
    val mapper = jacksonObjectMapper()
    val config = mapper.readValue<KafkaConfiguration>(File("kafka_conf.json"))

    val streams = KafkaStreams(
            KStreamBuilder().apply { constructStreams(this) },
            retrieveKafkaConfiguration(config)
    )
    streams.start()
    streams.addShutdownHook()
    */

    val gen = SBatchGenerator("sdu.sci.dev@gmail.com")
    val parameters = mapOf(
            "greeting" to "testGreeting"
    )

    val app = ApplicationDAO.findAllByName("hello").first()
    gen.generate(app, parameters, "/test/a/b/c")
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

data class SimpleDuration(val hours: Int, val minutes: Int, val seconds: Int) {
    init {
        if (seconds !in 0..59) throw IllegalArgumentException("seconds must be in 0..59")
        if (minutes !in 0..59) throw IllegalArgumentException("minutes must be in 0..59")
    }

    override fun toString() = StringBuilder().apply {
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }.toString()
}

data class NameAndVersion(val name: String, val version: String)

data class ToolDescription(
        val info: NameAndVersion,
        val container: String,
        val defaultNumberOfNodes: Int,
        val defaultTasksPerNode: Int,
        val defaultMaxTime: SimpleDuration,
        val requiredModules: List<String>
)

data class FileTransferDescription(val sourcePath: String, val locationPath: String)

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = ApplicationParameter.InputFile::class, name = "input_file"),
        JsonSubTypes.Type(value = ApplicationParameter.OutputFile::class, name = "output_file"),
        JsonSubTypes.Type(value = ApplicationParameter.Text::class, name = "text"),
        JsonSubTypes.Type(value = ApplicationParameter.Integer::class, name = "integer"),
        JsonSubTypes.Type(value = ApplicationParameter.FloatingPoint::class, name = "floating_point"))
sealed class ApplicationParameter<out V : Any> {
    abstract val name: String
    abstract fun map(inputParameter: Any): V

    data class InputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription = inputParameter as FileTransferDescription
    }

    data class OutputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription = inputParameter as FileTransferDescription
    }

    data class Text(override val name: String) : ApplicationParameter<String>() {
        override fun map(inputParameter: Any): String = inputParameter.toString()
    }

    data class Integer(override val name: String, val min: Int? = null, val max: Int?) : ApplicationParameter<Int>() {
        override fun map(inputParameter: Any): Int = (inputParameter as? Int) ?: inputParameter.toString().toInt()
    }

    data class FloatingPoint(
            override val name: String,
            val min: Double?,
            val max: Double?
    ) : ApplicationParameter<Double>() {
        override fun map(inputParameter: Any): Double =
                (inputParameter as? Double) ?: inputParameter.toString().toDouble()
    }
}

data class ApplicationDescription(
        val tool: NameAndVersion,
        val info: NameAndVersion,
        val numberOfNodes: String?,
        val tasksPerNode: String?,
        val maxTime: String?,
        val invocationTemplate: String,
        // TODO We cannot have duplicates on param name!
        val parameters: List<ApplicationParameter<*>>
)
/*
data class ConfiguredApplication(
        val tool: ToolDescription,
        val application: ApplicationDescription,
        val numberOfNodes: Int,
        val tasksPerNode: Int,
        val maxTime: Period,
        val inputFiles: List<FileTransferDescription>,
        val outputFiles: List<FileTransferDescription>,
        val containerInvocation: List<String>
)
*/

object ToolDAO {
    private val inMemoryDB = mapOf(
            "hello_world" to listOf(
                    ToolDescription(
                            info = NameAndVersion("hello_world", "1.0.0"),
                            container = "hello.simg",
                            defaultNumberOfNodes = 1,
                            defaultTasksPerNode = 1,
                            defaultMaxTime = SimpleDuration(hours = 0, minutes = 1, seconds = 0),
                            requiredModules = emptyList()
                    )
            )
    )

    fun findByNameAndVerison(name: String, version: String): ToolDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ToolDescription> = inMemoryDB[name] ?: emptyList()
}

object ApplicationDAO {
    private val inMemoryDB = mapOf(
            "hello" to listOf(
                    ApplicationDescription(
                            tool = NameAndVersion("hello_world", "1.0.0"),
                            info = NameAndVersion("hello", "1.0.0"),
                            numberOfNodes = null,
                            tasksPerNode = null,
                            maxTime = null,
                            invocationTemplate = "--greeting \$greeting",
                            parameters = listOf(ApplicationParameter.Text("greeting"))
                    )
            )
    )

    fun findByNameAndVersion(name: String, version: String): ApplicationDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ApplicationDescription> = inMemoryDB[name] ?: emptyList()
}

// Model
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = ABCApplicationEvent.Start::class, name = "start"),
        JsonSubTypes.Type(value = ABCApplicationEvent.Cancel::class, name = "cancel"))
sealed class ABCApplicationEvent {
    data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : ABCApplicationEvent()
    data class Cancel(val jobId: Long) : ABCApplicationEvent()
}
