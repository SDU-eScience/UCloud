package dk.sdu.cloud.storage.processor

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.service.RequestHeader
import dk.sdu.cloud.service.TokenValidation
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import dk.sdu.cloud.storage.Error
import dk.sdu.cloud.storage.Result
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import dk.sdu.cloud.storage.model.addShutdownHook
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/*
 * This file starts the Storage model. This will start up both the REST service and the Kafka consumer.
 * It might make sense to split these, but currently it seems that it actually makes quite a bit of sense to keep
 * these together. It will also be simpler, so we should do this for now.
 */

data class Configuration(
        val storage: StorageConfiguration,
        val service: ServiceConfiguration,
        val kafka: KafkaConfiguration,
        val tus: TusConfiguration?
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun parseFile(file: File) = mapper.readValue<Configuration>(file)
    }
}

data class StorageConfiguration(
        val host: String,
        val port: Int,
        val zone: String,
        val resource: String,
        val authScheme: String?,
        val sslPolicy: String?
)

data class ServiceConfiguration(val port: Int)
data class KafkaConfiguration(val servers: List<String>)

data class TusConfiguration(
        val icatJdbcUrl: String,
        val icatUser: String,
        val icatPassword: String
)

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("Server")
    log.info("Starting storage service")

    val filePath = File(if (args.size == 1) args[0] else "/etc/storage/conf.json")
    log.info("Reading configuration file from: ${filePath.absolutePath}")
    if (!filePath.exists()) {
        log.error("Could not find log file!")
        System.exit(1)
        return
    }

    val configuration = Configuration.parseFile(filePath)

    val storageService = with(configuration.storage) {
        IRodsStorageService(
                IRodsConnectionInformation(
                        host = host,
                        port = port,
                        zone = zone,
                        storageResource = resource,
                        authScheme = if (authScheme != null) AuthScheme.valueOf(authScheme) else AuthScheme.STANDARD,
                        sslNegotiationPolicy =
                        if (sslPolicy != null)
                            ClientServerNegotiationPolicy.SslNegotiationPolicy.valueOf(sslPolicy)
                        else
                            ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
                )
        )
    }

    val streamProcessor = StorageStreamProcessor(storageService, configuration.kafka)
    val restServer = StorageRestServer(configuration, storageService).create()

    val streams = KafkaStreams(
            StreamsBuilder().apply { streamProcessor.constructStreams(this) }.build(),
            streamProcessor.retrieveKafkaConfiguration()
    )

    streams.setStateListener { newState, _ ->
        when (newState) {
            KafkaStreams.State.PENDING_SHUTDOWN, KafkaStreams.State.NOT_RUNNING, KafkaStreams.State.ERROR -> {
                restServer.stop(0, 30, TimeUnit.SECONDS)
            }

            else -> {
                // Ignore
            }
        }
    }

    // From this we can't even extract which job was the problem. We probably need to handle this some other way.
    // We do not want a single bad job to take down the entire thing.
    streams.setUncaughtExceptionHandler { _, _ ->
        // We should probably still log what happened...
        // mapResult could help with exception handling though.
    }

    // Start the stream processor
    log.info("Starting stream processor")
    streams.start()
    streams.addShutdownHook()

    // Start the rest model
    log.info("Starting REST service")
    restServer.start()
}

abstract class StorageService {
    abstract val storageFactory: StorageConnectionFactory

    /**
     * Should validate the StorageRequest and provide us with an appropriate StorageConnection. For internal
     * services this should simply match an appropriate AdminConnection.
     *
     * For operations performed by an end-user this should match their internal storage user.
     *
     * If the supplied credentials are incorrect we should return an [Error]
     *
     * If any of our collaborators are unavailable we should throw an exception (after perhaps internally re-trying).
     * This will cause Kafka to _not_ commit this message as having been consumed by this sub-system. Which is good,
     * because we want to retry at a later point.
     */
    fun validateRequest(header: RequestHeader): Result<StorageConnection> {
        // TODO We need to close the storage connections.
        return with(header) {
            val validated = TokenValidation.validateOrNull(performedFor) ?: return Error.invalidAuthentication()
            storageFactory.createForAccount(validated.subject, validated.token)
        }
    }
}

class IRodsStorageService(
        connectionInformation: IRodsConnectionInformation
) : StorageService() {
    override val storageFactory: StorageConnectionFactory = IRodsStorageConnectionFactory(connectionInformation)
}