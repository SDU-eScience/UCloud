package org.esciencecloud.abc

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.httpMethod
import io.ktor.request.uri
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.StreamsMetadata
import org.esciencecloud.asynchttp.HttpClient
import org.esciencecloud.asynchttp.setJsonBody
import org.esciencecloud.kafka.JsonSerde.jsonSerde
import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
import org.esciencecloud.storage.ext.StorageException
import org.esciencecloud.storage.ext.irods.IRodsConnectionInformation
import org.esciencecloud.storage.ext.irods.IRodsStorageConnectionFactory
import org.esciencecloud.storage.model.FileStat
import org.esciencecloud.storage.model.StoragePath
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.util.*

data class KafkaConfiguration(val servers: List<String>)

class ApplicationStreamProcessor(
        private val kafkaConfig: KafkaConfiguration,
        private val sshConfig: SimpleConfiguration,
        private val storageConnectionFactory: StorageConnectionFactory,
        private val hostname: String,
        private val rpcPort: Int
) {
    companion object {
        const val TOPIC_HPC_APP_REQUESTS = "hpcAppRequests"
        const val TOPIC_HPC_APP_EVENTS = "hpcAppEvents"
        const val TOPIC_SLURM_TO_JOB_ID = "slurmIdToJobId"
    }

    private val log = LoggerFactory.getLogger(ApplicationStreamProcessor::class.java)

    private val sbatchGenerator = SBatchGenerator("sdu.esci.dev@gmail.com")
    private var initialized = false
    private lateinit var streamProcessor: KafkaStreams

    private fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers.joinToString(",")
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        properties[StreamsConfig.APPLICATION_SERVER_CONFIG] = "$hostname:$rpcPort"
        return properties
    }

    private data class ValidatedFileForUpload(
            val stat: FileStat,
            val destinationFileName: String,
            val destinationPath: String,
            val sourcePath: StoragePath
    )

    private fun validateInputFiles(app: ApplicationDescription, parameters: Map<String, Any>,
                                   storage: StorageConnection, workDir: URI): Result<List<ValidatedFileForUpload>> {
        val result = ArrayList<ValidatedFileForUpload>()

        for (input in app.parameters.filterIsInstance<ApplicationParameter.InputFile>()) {
            val inputParameter = parameters[input.name] ?:
                    return Error.invalidMessage("Missing input parameter: " + input.name)

            val transferDescription = input.map(inputParameter)
            val sourcePath = StoragePath.fromURI(transferDescription.source)

            val stat = storage.fileQuery.stat(sourcePath).capture() ?:
                    return Error.invalidMessage("Missing file in storage: $sourcePath. Are you sure it exists?")

            // Resolve relative path against working directory. Ensure that file is still inside of
            // the working directory.
            val destinationPath = workDir.resolve(URI(transferDescription.destination)).normalize().path
            if (!destinationPath.startsWith(workDir.path)) {
                return Error.invalidMessage("Not allowed to leave working " +
                        "directory via relative paths. Please avoid using '..' in paths.")
            }

            val name = destinationPath.split("/").last()

            result.add(ValidatedFileForUpload(stat, name, destinationPath, sourcePath))
        }
        return Ok(result)
    }

    private fun handleStartEvent(storage: StorageConnection, request: HPCAppRequest.Start): Result<Long> {
        val app = with(request.application) { ApplicationDAO.findByNameAndVersion(name, version) } ?:
                return Error.notFound("Could not find application ${request.application}")

        val parameters = request.parameters

        // Must end in '/'. Otherwise resolve will do the wrong thing
        val homeDir = URI("file:///home/${sshConfig.user}/projects/")
        val jobDir = homeDir.resolve("jobid/")
        val workDir = jobDir.resolve("files/")

        val job = try {
            sbatchGenerator.generate(app, parameters, workDir.path)
        } catch (ex: Exception) {
            // TODO Should probably return a result?
            return Error(500, "Unable to generate slurm job")
        }

        val validatedFiles = validateInputFiles(app, parameters, storage, workDir).capture() ?:
                return Result.lastError()

        SSHConnection.connect(sshConfig).use { ssh ->
            // Transfer (validated) input files
            validatedFiles.forEach { upload ->
                var errorDuringUpload: Error<Long>? = null
                // TODO FIXME Destination name should be escaped
                // TODO FIXME Destination name should be escaped
                // TODO FIXME Destination name should be escaped
                ssh.scp(upload.stat.sizeInBytes, upload.destinationFileName, upload.destinationPath,
                        "0644") {
                    try {
                        storage.files.get(upload.sourcePath, it) // TODO This will need to change
                    } catch (ex: StorageException) {
                        errorDuringUpload = Error.permissionDenied("Not allowed to access file: ${upload.sourcePath}")
                    }
                }
                if (errorDuringUpload != null) return errorDuringUpload as Error<Long>
            }

            // Transfer job file
            val jobLocation = jobDir.resolve("job.sh").normalize()
            val serializedJob = job.toByteArray()
            ssh.scp(serializedJob.size.toLong(), "job.sh", jobLocation.path, "0644") {
                it.write(serializedJob)
            }

            // Submit job file
            val (_, output, slurmJobId) = ssh.sbatch(jobLocation.path)
            // TODO Need to revisit the idea of status codes
            // Crashing right here would cause incorrect resubmission of job to HPC
            return if (slurmJobId == null) Error(500, output) else Ok(slurmJobId)
        }
    }

    private fun constructStreams(builder: KStreamBuilder) {
        // Handle requests and write to events topic
        val requests = builder.stream<String, Request<HPCAppRequest>>(Serdes.String(), jsonSerde(), TOPIC_HPC_APP_REQUESTS)
        val events = requests
                .mapValues { request ->
                    log.info("WE'VE GOT A MESSAGE! $request")
                    val storage = validateAndConnectToStorage(request.header).capture() ?:
                            return@mapValues HPCAppEvent.UnsuccessfullyCompleted(Error.invalidAuthentication())

                    // TODO We still need a clear plan for how to deal with this during replays.
                    when (request.event) {
                        is HPCAppRequest.Start -> {
                            val result = handleStartEvent(storage, request.event)
                            when (result) {
                                is Ok<Long> -> HPCAppEvent.Started(result.result)
                                is Error<Long> -> HPCAppEvent.UnsuccessfullyCompleted(result)
                            }
                        }

                        is HPCAppRequest.Cancel -> {
                            HPCAppEvent.UnsuccessfullyCompleted(Error.invalidMessage())
                        }
                    }
                }
                .through(Serdes.String(), jsonSerde(), TOPIC_HPC_APP_EVENTS)

        // Keep a mapping between slurm ids and job ids
        events
                .filter { _, kafkaEvent -> kafkaEvent is HPCAppEvent.Started }
                .map { systemId, event -> KeyValue((event as HPCAppEvent.Started).jobId, systemId) }
                .groupByKey(Serdes.Long(), Serdes.String())
                .aggregate(
                        { null }, // aggregate initializer
                        { _, newValue, _ -> newValue }, // aggregator
                        Serdes.String(), // value serde
                        TOPIC_SLURM_TO_JOB_ID // table name
                )
    }

    fun start() {
        if (initialized) throw IllegalStateException("Already started!")
        streamProcessor = KafkaStreams(KStreamBuilder().apply { constructStreams(this) }, retrieveKafkaConfiguration())
        // TODO How do we handle deserialization exceptions???
        streamProcessor.start()
        streamProcessor.addShutdownHook()
        createRPCServer().start()
        initialized = true
    }

    fun stop() {
        streamProcessor.close()
    }

    private fun validateAndConnectToStorage(requestHeader: RequestHeader): Result<StorageConnection> =
            with(requestHeader.performedFor) { storageConnectionFactory.createForAccount(username, password) }

    private fun KafkaStreams.addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread { this.close() })
    }


    private fun createRPCServer() = embeddedServer(CIO, port = rpcPort) {
        // TODO Need to validate via certificates
        install(CallLogging)
        install(DefaultHeaders)
        install(ContentNegotiation) {
            jackson { registerKotlinModule() }
        }

        routing {
            get("/slurm/{key}") {
                val key = call.requireParamOrRespond("key") { it.toLongOrNull() } ?: return@get

                call.lookupKafkaStoreOrProxy<Long, String>(
                        HostInfo(hostname, rpcPort),
                        TOPIC_SLURM_TO_JOB_ID,
                        key,
                        Serdes.Long()
                )
            }
        }
    }

    private suspend fun <T : Any> ApplicationCall.requireParamOrRespond(key: String, mapper: (String) -> T?): T? {
        val input = parameters[key]
        if (input == null) {
            respond(HttpStatusCode.BadRequest, "Bad request")
            return null
        }

        val mapped = mapper(input)
        if (mapped == null) {
            respond(HttpStatusCode.BadRequest, "Bad request")
            return null
        }
        return mapped
    }

    private suspend fun <Key, Value> ApplicationCall.lookupKafkaStoreOrProxy(
            thisHost: HostInfo,
            table: String,
            key: Key,
            keySerde: Serde<Key>,
            payload: Any? = null
    ) {
        // TODO Move this out, we don't want to create a new one every lookup
        val log = LoggerFactory.getLogger("org.esciencecloud.kafka.lookupKafkaStoreOrProxy")

        val hostWithData = streamProcessor.metadataForKey(table, key, keySerde.serializer())
        when {
            hostWithData == StreamsMetadata.NOT_AVAILABLE -> respond(HttpStatusCode.NotFound)

            thisHost == hostWithData.hostInfo() -> {
                val store = streamProcessor.store(table, QueryableStoreTypes.keyValueStore<Key, Value>())
                val value = store[key] ?: return run {
                    log.error("Expected value to be found in local server")
                    respond(HttpStatusCode.InternalServerError)
                }

                respond(value)
            }

            else -> {
                val rawUri = request.uri // This includes query params
                val uri = if (!rawUri.startsWith("/")) "/$rawUri" else rawUri

                val endpoint = "http://${hostWithData.host()}:${hostWithData.port()}$uri"
                val response = HttpClient.post(endpoint) {
                    if (payload != null) setJsonBody(payload)
                    setMethod(request.httpMethod.value)
                }
                respond(response)
            }
        }
    }
}


fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()
    val kafkaConfig = mapper.readValue<KafkaConfiguration>(File("kafka_conf.json"))
    val sshConfig = mapper.readValue<SimpleConfiguration>(File("ssh_conf.json"))

    val irodsConnectionFactory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = "localhost",
            zone = "tempZone",
            port = 1247,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
            authScheme = AuthScheme.STANDARD
    ))

    val processor = ApplicationStreamProcessor(kafkaConfig, sshConfig, irodsConnectionFactory, "localhost", 42200)
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

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}

data class ToolDescription(
        val info: NameAndVersion,
        val container: String,
        val defaultNumberOfNodes: Int,
        val defaultTasksPerNode: Int,
        val defaultMaxTime: SimpleDuration,
        val requiredModules: List<String>
)

data class FileTransferDescription(val source: String, val destination: String)

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
sealed class ApplicationParameter<V : Any> {
    abstract val name: String
    abstract fun map(inputParameter: Any): V
    abstract fun toInvocationArgument(entry: V): String

    data class InputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination = params["destination"] as String? ?:
                    throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String = entry.destination
    }

    data class OutputFile(override val name: String) : ApplicationParameter<FileTransferDescription>() {
        override fun map(inputParameter: Any): FileTransferDescription {
            @Suppress("UNCHECKED_CAST")
            val params = inputParameter as? Map<String, Any> ?: throw IllegalArgumentException("Invalid user input")
            val source = params["source"] as String? ?: throw IllegalArgumentException("Missing source property")
            val destination = params["destination"] as String? ?:
                    throw IllegalArgumentException("Missing destination property")

            return FileTransferDescription(source, destination)
        }

        override fun toInvocationArgument(entry: FileTransferDescription): String = entry.source
    }

    data class Text(override val name: String) : ApplicationParameter<String>() {
        override fun map(inputParameter: Any): String = inputParameter.toString()

        override fun toInvocationArgument(entry: String): String = entry
    }

    data class Integer(override val name: String, val min: Int? = null, val max: Int?) : ApplicationParameter<Int>() {
        override fun map(inputParameter: Any): Int = (inputParameter as? Int) ?: inputParameter.toString().toInt()

        override fun toInvocationArgument(entry: Int): String = entry.toString()
    }

    data class FloatingPoint(
            override val name: String,
            val min: Double?,
            val max: Double?
    ) : ApplicationParameter<Double>() {
        override fun map(inputParameter: Any): Double =
                (inputParameter as? Double) ?: inputParameter.toString().toDouble()

        override fun toInvocationArgument(entry: Double): String = entry.toString()
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

object ToolDAO {
    val inMemoryDB = mutableMapOf(
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

    fun findByNameAndVersion(name: String, version: String): ToolDescription? =
            inMemoryDB[name]?.find { it.info.version == version }

    fun findAllByName(name: String): List<ToolDescription> = inMemoryDB[name] ?: emptyList()
}

object ApplicationDAO {
    val inMemoryDB = mutableMapOf(
            "hello" to listOf(
                    ApplicationDescription(
                            tool = NameAndVersion("hello_world", "1.0.0"),
                            info = NameAndVersion("hello", "1.0.0"),
                            numberOfNodes = null,
                            tasksPerNode = null,
                            maxTime = null,
                            invocationTemplate = "--greeting \$greeting \$infile \$outfile",
                            parameters = listOf(
                                    ApplicationParameter.Text("greeting"),
                                    ApplicationParameter.InputFile("infile"),
                                    ApplicationParameter.OutputFile("outfile")
                            )
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
        JsonSubTypes.Type(value = HPCAppRequest.Start::class, name = "start"),
        JsonSubTypes.Type(value = HPCAppRequest.Cancel::class, name = "cancel"))
sealed class HPCAppRequest {
    data class Start(val application: NameAndVersion, val parameters: Map<String, Any>) : HPCAppRequest()
    data class Cancel(val jobId: Long) : HPCAppRequest()
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = Request.TYPE_PROPERTY)
@JsonSubTypes(
        JsonSubTypes.Type(value = HPCAppEvent.Started::class, name = "started"),
        JsonSubTypes.Type(value = HPCAppEvent.SuccessfullyCompleted::class, name = "success"),
        JsonSubTypes.Type(value = HPCAppEvent.UnsuccessfullyCompleted::class, name = "error"))
sealed class HPCAppEvent {
    data class Started(val jobId: Long) : HPCAppEvent()

    abstract class Ended : HPCAppEvent() {
        abstract val success: Boolean
    }

    data class SuccessfullyCompleted(val jobId: Long) : Ended() {
        override val success: Boolean = true
    }

    data class UnsuccessfullyCompleted(val reason: Error<Any>) : Ended() {
        override val success: Boolean = false
    }
}
