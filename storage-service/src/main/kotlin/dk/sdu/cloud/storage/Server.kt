package dk.sdu.cloud.storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.zafarkhaja.semver.Version
import dk.sdu.cloud.auth.api.AuthStreams
import dk.sdu.cloud.service.*
import dk.sdu.cloud.storage.api.StorageBuildConfig
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import dk.sdu.cloud.storage.ext.irods.IRodsConnectionInformation
import dk.sdu.cloud.storage.ext.irods.IRodsStorageConnectionFactory
import dk.sdu.cloud.storage.processor.UserProcessor
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.slf4j.LoggerFactory
import java.io.File

/*
 * This file starts the Storage model. This will start up both the REST service and the Kafka consumer.
 * It might make sense to split these, but currently it seems that it actually makes quite a bit of sense to keep
 * these together. It will also be simpler, so we should do this for now.
 */

data class Configuration(
        val storage: StorageConfiguration,
        val service: ServiceConfiguration,
        val kafka: KafkaConfiguration,
        val zookeeper: ZooKeeperHostInfo,
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

data class ServiceConfiguration(val hostname: String, val port: Int)
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

    val definition = ServiceDefinition(StorageBuildConfig.Name, Version.valueOf(StorageBuildConfig.Version))
    val instance = ServiceInstance(definition, configuration.service.hostname, configuration.service.port)

    val (zk, node) = runBlocking {
        val zk = ZooKeeperConnection(listOf(configuration.zookeeper)).connect()
        val node = zk.registerService(instance)
        Pair(zk, node)
    }

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

    // TODO We need the iRODS PAM module to accept JWTs from services that grant access to an admin account
    val giantHack = with(configuration.storage) {
        IRodsStorageConnectionFactory(
                IRodsConnectionInformation(
                        host = host,
                        port = port,
                        zone = zone,
                        storageResource = resource,
                        authScheme = AuthScheme.STANDARD,
                        sslNegotiationPolicy =
                        if (sslPolicy != null)
                            ClientServerNegotiationPolicy.SslNegotiationPolicy.valueOf(sslPolicy)
                        else
                            ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
                )
        )
    }

    val adminAccount = giantHack.createForAccount("rods", "rods").orThrow()

    val builder = StreamsBuilder()
    UserProcessor(builder.stream(AuthStreams.UserUpdateStream), adminAccount).init()
    val kafkaStreams = KafkaStreams(builder.build(), KafkaUtil.retrieveKafkaStreamsConfiguration(
            configuration.kafka.servers,
            StorageBuildConfig.Name,
            configuration.service.hostname,
            configuration.service.port
    ))

    kafkaStreams.start()
    // TODO Catch exceptions in Kafka


    val restServer = StorageRestServer(configuration, storageService).create()
    // Start the REST server
    log.info("Starting REST service")
    restServer.start()

    runBlocking { zk.markServiceAsReady(node, instance) }
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
        return with(header) {
            val decoded = TokenValidation.validateOrNull(performedFor) ?: return Error.invalidAuthentication()
            storageFactory.createForAccount(decoded.subject, decoded.token)
        }
    }
}

class IRodsStorageService(
        connectionInformation: IRodsConnectionInformation
) : StorageService() {
    override val storageFactory: StorageConnectionFactory = IRodsStorageConnectionFactory(connectionInformation)
}