package org.esciencecloud.storage.server

import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.esciencecloud.storage.*
import org.esciencecloud.storage.irods.IRodsConnectionInformation
import org.esciencecloud.storage.irods.IRodsStorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/*
 * This file starts the Storage server. This will start up both the REST service and the Kafka consumer.
 * It might make sense to split these, but currently it seems that it actually makes quite a bit of sense to keep
 * these together. It will also be simpler, so we should do this for now.
 */

fun <T : Any, InputType : Any> Result<T>.toResponse(input: Request<InputType>) =
        Response(this is Ok, (this as? Error)?.message, input)

fun main(args: Array<String>) {
    val storageService = IRodsStorageService(
            IRodsConnectionInformation(
                    host = "localhost",
                    port = 1247,
                    zone = "tempZone",
                    storageResource = "radosRandomResc",
                    authScheme = AuthScheme.STANDARD,
                    sslNegotiationPolicy = ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
            ),

            adminUsername = "rods",
            adminPassword = "rods"
    )

    val streamProcessor = StorageStreamProcessor(storageService)

    // TODO This should probably use their Application class thing. Look into this
    val restServer = StorageRestServer(42100, storageService).create()

    val streams = KafkaStreams(
            KStreamBuilder().apply { streamProcessor.constructStreams(this) },
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
    val log = LoggerFactory.getLogger("Server")

    // Start the stream processor
    log.info("Starting stream processor")
    streams.start()
    streams.addShutdownHook()

    // Start the rest server
    log.info("Starting REST service")
    restServer.start()
}

abstract class StorageService {
    abstract val storageFactory: StorageConnectionFactory
    abstract val adminConnection: StorageConnection

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
        // TODO
        if (header.performedFor.username == "internal-service-super-secret-obviously-dont-use-this") {
            // This should use API tokens or just plain certificates for validating that this is an internal service
            // making the request.
            return Ok(adminConnection)
        }

        return try {
            Ok(with(header.performedFor) { storageFactory.createForAccount(username, password) })
        } catch (ex: PermissionException) {
            // TODO Change interface
            Error.permissionDenied()
        }
    }
}

class IRodsStorageService(
        connectionInformation: IRodsConnectionInformation,
        adminUsername: String = "rods",
        adminPassword: String = "rods"
) : StorageService() {
    override val storageFactory: StorageConnectionFactory = IRodsStorageConnectionFactory(connectionInformation)
    override val adminConnection: StorageConnection = storageFactory.createForAccount(adminUsername, adminPassword)
}