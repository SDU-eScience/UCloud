package org.esciencecloud.abc

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
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serde
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.StreamsMetadata
import org.esciencecloud.abc.api.*
import org.esciencecloud.abc.ssh.SSHConnection
import org.esciencecloud.abc.ssh.SimpleSSHConfig
import org.esciencecloud.abc.ssh.sbatch
import org.esciencecloud.abc.ssh.scpUpload
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
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.util.*

data class KafkaConfiguration(val servers: List<String>)

class ApplicationStreamProcessor(
        private val kafkaConfig: KafkaConfiguration,
        private val sshConfig: SimpleSSHConfig,
        private val mailConfig: MailAgentConfiguration,
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
    private lateinit var mailAgent: MailAgent

    private fun retrieveKafkaConfiguration(): Properties {
        val properties = Properties()
        properties[StreamsConfig.APPLICATION_ID_CONFIG] = "storage-processor"
        properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers.joinToString(",")
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // Don't miss any events
        properties[StreamsConfig.APPLICATION_SERVER_CONFIG] = "$hostname:$rpcPort"

        properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers.joinToString(",")
        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName!!
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
            log.warn("Unable to generate slurm job:")
            log.warn(ex.stackTraceToString())
            return Error.internalError()
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
                ssh.scpUpload(upload.stat.sizeInBytes, upload.destinationFileName, upload.destinationPath,
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
            ssh.scpUpload(serializedJob.size.toLong(), "job.sh", jobLocation.path, "0644") {
                it.write(serializedJob)
            }

            // Submit job file
            val (_, output, slurmJobId) = ssh.sbatch(jobLocation.path)
            // TODO Need to revisit the idea of status codes
            // Crashing right here would cause incorrect resubmission of job to HPC
            return if (slurmJobId == null) {
                log.warn("Got back a null slurm job ID!")
                log.warn("Output from job: $output")
                log.warn("Generated slurm file:")
                log.warn(job)

                Error.internalError()
            } else {
                Ok(slurmJobId)
            }
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
        val kafkaConfig = retrieveKafkaConfiguration()

        // TODO How do we handle deserialization exceptions???
        log.info("Starting Kafka Streams Processor")
        streamProcessor = KafkaStreams(KStreamBuilder().apply { constructStreams(this) }, kafkaConfig)
        streamProcessor.start()
        streamProcessor.addShutdownHook()

        log.info("Starting RPC Server")
        createRPCServer().start()

        log.info("Starting Kafka Producer (Mail Agent)")
        // TODO Should only use the producer config, not the streams config
        val producer = KafkaProducer<String, String>(kafkaConfig)

        log.info("Starting Mail Agent")
        val mapper = jacksonObjectMapper()
        mailAgent = MailAgent(mailConfig)
        mailAgent.addListener { event ->
            when (event) {
                is SlurmEventBegan -> {
                    val key = runBlocking {
                        lookupKafkaStoreOrProxy("/slurm/${event.jobId}", "GET", HostInfo(hostname, rpcPort),
                                TOPIC_SLURM_TO_JOB_ID, event.jobId, Serdes.Long(), Serdes.String())
                    }.orThrow()

                    val appEvent = mapper.writeValueAsString(HPCAppEvent.Started(event.jobId))
                    producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))
                }

                is SlurmEventEnded -> {
                    // TODO Not sure if throwing is the right choice here, but not sure how else to handle it
                    val key = runBlocking {
                        lookupKafkaStoreOrProxy("/slurm/${event.jobId}", "GET", HostInfo(hostname, rpcPort),
                                TOPIC_SLURM_TO_JOB_ID, event.jobId, Serdes.Long(), Serdes.String())
                    }.orThrow()

                    val appEvent = mapper.writeValueAsString(HPCAppEvent.SuccessfullyCompleted(event.jobId))
                    producer.send(ProducerRecord(TOPIC_HPC_APP_EVENTS, key, appEvent))

                    // TODO Transfer output files
                    // TODO Clean up after job
                }
            }
        }
        mailAgent.start()

        log.info("Ready!")
        initialized = true
    }

    fun stop() {
        mailAgent.stop()
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

                call.respondInteractiveQuery<Long, String>(
                        HostInfo(hostname, rpcPort),
                        TOPIC_SLURM_TO_JOB_ID,
                        key,
                        Serdes.Long(),
                        Serdes.String()
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

    private suspend fun <Key : Any, Value : Any> ApplicationCall.respondInteractiveQuery(
            thisHost: HostInfo,
            table: String,
            key: Key,
            keySerde: Serde<Key>,
            valueSerde: Serde<Value>,
            payload: Any? = null
    ) {
        val result = lookupKafkaStoreOrProxy(request.uri, request.httpMethod.value, thisHost, table, key,
                keySerde, valueSerde, payload)

        when (result) {
            is Ok -> respond(result.result)
        // TODO We need an http error code for results
            is Error -> respond(HttpStatusCode.fromValue(result.errorCode), result.message)
        }
    }

    private suspend fun <Key : Any, Value : Any> lookupKafkaStoreOrProxy(
            rpcEndpoint: String,
            rpcHttpMethod: String,
            thisHost: HostInfo,
            table: String,
            key: Key,
            keySerde: Serde<Key>,
            valueSerde: Serde<Value>,
            payload: Any? = null
    ): Result<Value> {
        val hostWithData = streamProcessor.metadataForKey(table, key, keySerde.serializer())
        return when {
            hostWithData == StreamsMetadata.NOT_AVAILABLE -> Error.notFound()

            thisHost == hostWithData.hostInfo() -> {
                val store = streamProcessor.store(table, QueryableStoreTypes.keyValueStore<Key, Value>())
                val value = store[key] ?: return run {
                    log.error("Expected value to be found in local server")
                    log.error("Table: $table, key: $key")

                    Error.internalError()
                }

                Ok(value)
            }

            else -> {
                val uri = if (!rpcEndpoint.startsWith("/")) "/$rpcEndpoint" else rpcEndpoint

                val endpoint = "http://${hostWithData.host()}:${hostWithData.port()}$uri"
                val response = HttpClient.post(endpoint) {
                    if (payload != null) setJsonBody(payload)
                    setMethod(rpcHttpMethod)
                }

                return try {
                    Ok(valueSerde.deserializer().deserialize("", response.responseBodyAsBytes))
                } catch (ex: Exception) {
                    log.warn("Unable to deserialize response from other Kafka store.")
                    log.warn("Endpoint was: $endpoint")
                    log.warn("Table: $table, key: $key")
                    log.warn("Raw response: ${response.responseBody} (${response.statusCode})")
                    log.warn("Exception: ${ex.stackTraceToString()}")

                    Error.internalError()
                }
            }
        }
    }
}

fun <T : Any> Error.Companion.internalError(): Error<T> = Error(500, "Internal error")
fun Exception.stackTraceToString(): String = StringWriter().apply { printStackTrace(PrintWriter(this)) }.toString()


fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()
    val kafkaConfig = mapper.readValue<KafkaConfiguration>(File("kafka_conf.json"))
    val sshConfig = mapper.readValue<SimpleSSHConfig>(File("ssh_conf.json"))
    val mailConfig = mapper.readValue<MailAgentConfiguration>(File("mail_conf.json"))

    val irodsConnectionFactory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
            host = "localhost",
            zone = "tempZone",
            port = 1247,
            storageResource = "radosRandomResc",
            sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE,
            authScheme = AuthScheme.STANDARD
    ))

    val processor = ApplicationStreamProcessor(kafkaConfig, sshConfig, mailConfig,
            irodsConnectionFactory, "localhost", 42200)
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

