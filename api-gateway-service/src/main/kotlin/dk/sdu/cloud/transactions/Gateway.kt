package dk.sdu.cloud.transactions

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import org.esciencecloud.service.ZooKeeperConnection
import org.esciencecloud.service.ZooKeeperHostInfo
import dk.sdu.cloud.transactions.util.stackTraceToString
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


fun main(args: Array<String>) = runBlocking {
    val log = LoggerFactory.getLogger("Gateway")

    val manager = ServiceManager(File("/tmp/gw-target"), File("/Users/dthrane/Dropbox/work/sdu-cloud/app-service/build/libs"))
    val scanner = Scanner(System.`in`)

    val producerConfig = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer",
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to "org.apache.kafka.common.serialization.StringSerializer"
    )

    val kafkaProducer = KafkaProducer<String, String>(producerConfig)

    log.info("Connecting to ZooKeeper")
    val zk = ZooKeeperConnection(listOf(ZooKeeperHostInfo("localhost"))).connect()
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