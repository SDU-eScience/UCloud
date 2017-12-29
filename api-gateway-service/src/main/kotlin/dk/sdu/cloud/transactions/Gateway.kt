package dk.sdu.cloud.transactions

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.ZooKeeperConnection
import dk.sdu.cloud.service.ZooKeeperHostInfo
import dk.sdu.cloud.transactions.util.stackTraceToString
import io.ktor.application.install
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

data class GatewayConfiguration(
        val targets: List<String>,
        val hostname: String,
        val zookeeper: ZooKeeperHostInfo,
        val kafka: KafkaConfiguration
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

    val targets = config.targets.map { File(it) }
    val manager = ServiceManager(*targets.toTypedArray())
    val scanner = Scanner(System.`in`)

    val producerConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to config.kafka.servers.joinToString(","),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
    )

    val kafkaProducer = KafkaProducer<String, String>(producerConfig)

    log.info("Connecting to ZooKeeper")
    val zk = ZooKeeperConnection(listOf(config.zookeeper)).connect()
    log.info("Connected!")

    var currentServer: ApplicationEngine? = null
    while (true) {
        log.info("Reloading service definitions!")
        try {
            val definitions = manager.load()
            val rest = RESTProxy(definitions, zk)
            val kafka = KafkaProxy(definitions, kafkaProducer)

            if (currentServer != null) {
                // We have to restart server to reload routes.
                // We might be able to solve this with some kind of dynamic routes. But this will work for now.
                log.info("Stopping existing server (timeout 35s)")
                currentServer.stop(5, 35, TimeUnit.SECONDS)
            }

            log.info("Ready to configure new server!")
            currentServer = embeddedServer(CIO, port = 8080) {
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

            scanner.nextLine()
        } catch (ex: Exception) {
            log.warn("Caught exception while reloading service definitions!")
            log.warn(ex.stackTraceToString())
            if (currentServer == null) {
                log.error("Exiting...")
                System.exit(1)
                return@runBlocking
            }
        }
    }
}