package dk.sdu.cloud.transactions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.*
import dk.sdu.cloud.transactions.dev.HttpBin
import dk.sdu.cloud.transactions.dev.TransferSh
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.DefaultHeaders
import io.ktor.features.XForwardedHeadersSupport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.slf4j.LoggerFactory
import stackTraceToString
import java.io.File
import java.util.concurrent.TimeUnit

data class GatewayConfiguration(
        val targets: List<String>,
        val hostname: String,
        val zookeeper: ZooKeeperHostInfo,
        val kafka: KafkaConfiguration,
        val reloadSecret: String
)

data class KafkaConfiguration(
        val servers: List<String>
)

fun main(args: Array<String>) = runBlocking {
    val log = LoggerFactory.getLogger("Gateway")
    val objectMapper = jacksonObjectMapper()
    val config = objectMapper.readValue<GatewayConfiguration>(
            File(args.getOrElse(0) { "/etc/gateway/config.json" })
    )

    log.info("Connecting to ZooKeeper")
    val zk = ZooKeeperConnection(listOf(config.zookeeper)).connect()
    log.info("Connected!")

    RESTServerSupport.allowMissingKafkaHttpLogger = true

    val targets = config.targets.map { File(it) }
    val manager = DefaultServiceManager(*targets.toTypedArray()).let {
        when (args.getOrNull(1)) {
            "dev-bin" -> {
                val instance = ServiceInstance(HttpBin.definition(), "httpbin.org", 443)
                val node = zk.registerService(instance)
                zk.markServiceAsReady(node, instance)
                log.info("")
                log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                log.info("Running in development mode. Proxying requests to httpbin.org")
                log.info("Running in development mode. Proxying requests to httpbin.org")
                log.info("Running in development mode. Proxying requests to httpbin.org")
                log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                log.info("")
                DevelopmentServiceManager(listOf(HttpBin.definition), it)
            }

            "dev-transfer" -> {
                val instance = ServiceInstance(TransferSh.definition(), "transfer.sh", 443)
                val node = zk.registerService(instance)
                zk.markServiceAsReady(node, instance)
                log.info("")
                log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                log.info("Running in development mode. Proxying requests to transfer.sh")
                log.info("Running in development mode. Proxying requests to transfer.sh")
                log.info("Running in development mode. Proxying requests to transfer.sh")
                log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                log.info("")
                DevelopmentServiceManager(listOf(TransferSh.definition), it)
            }

            else -> {
                it
            }
        }
    }

    val producerConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to config.kafka.servers.joinToString(","),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
    )

    val kafkaProducer = KafkaProducer<String, String>(producerConfig)
    var currentServer: ApplicationEngine? = null

    fun restartServer() {
        try {
            log.info("Reloading service definitions!")
            val definitions = manager.load()
            val rest = RESTProxy(definitions, zk, kafkaProducer)
            val kafka = KafkaProxy(definitions, kafkaProducer)

            val capturedServer = currentServer
            if (capturedServer != null) {
                // We have to restart server to reload routes.
                // We might be able to solve this with some kind of dynamic routes. But this will work for now.
                log.info("Stopping existing server (timeout 35s)")
                capturedServer.stop(5, 35, TimeUnit.SECONDS)
            }

            log.info("Ready to configure new server!")
            currentServer = embeddedServer(Netty, port = 8080) {
                install(XForwardedHeadersSupport)
                install(DefaultHeaders) {
                    header(HttpHeaders.Server, "cloud.sdu.dk")
                }
                install(CallLogging)
                install(ContentNegotiation) {
                    jackson { registerKotlinModule() }
                }

                routing {
                    rest.configure(this)
                    kafka.configure(this)
                }
            }.start(wait = false)

            log.info("New server is ready!")
        } catch (ex: Exception) {
            log.warn("Caught exception while reloading service definitions!")
            log.warn(ex.stackTraceToString())
            if (currentServer == null) {
                log.error("Exiting...")
                System.exit(1)
            }
        }
    }

    val reloadServer = embeddedServer(Netty, port = 8081) {
        routing {
            post("/reload") {
                val incomingSecret = call.request.header("Reload-Secret") ?: run {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }

                if (incomingSecret != config.reloadSecret) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }

                restartServer()
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    restartServer()
    reloadServer.start(wait = true)
    log.info("Gateway is ready!")
}