package dk.sdu.cloud.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.auth.api.*
import dk.sdu.cloud.auth.services.PersonUtils
import dk.sdu.cloud.auth.services.Principals
import dk.sdu.cloud.auth.services.RefreshTokens
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaProducerConfiguration
import dk.sdu.cloud.service.KafkaUtil.retrieveKafkaStreamsConfiguration
import dk.sdu.cloud.service.forStream
import kotlinx.coroutines.experimental.runBlocking
import org.apache.kafka.clients.producer.KafkaProducer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

private fun loadKeysAndInsertIntoProps(properties: Properties): Pair<RSAPublicKey, RSAPrivateKey> {
    val certs = File("certs").takeIf { it.exists() && it.isDirectory } ?:
            throw IllegalStateException("Missing 'certs' folder")
    val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile } ?:
            throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
    val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile } ?:
            throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

    val x509Text = x509Cert.readText()
    val privText = privateKey.readText()
    properties["onelogin.saml2.sp.x509cert"] = x509Text
    properties["onelogin.saml2.sp.privatekey"] = privText

    val loadedX509Cert = Util.loadCert(x509Text)
    val loadedPrivKey = Util.loadPrivateKey(privText)

    return Pair(
            loadedX509Cert.publicKey as RSAPublicKey,
            loadedPrivKey as RSAPrivateKey
    )
}


fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("ServerMain")

    val mapper = jacksonObjectMapper()
    val config = mapper.readValue<AuthConfiguration>(File("auth_config.json"))

    val hostname = "localhost"
    val port = 42300

    if (args.isEmpty()) {
        val samlProperties = Properties().apply {
            load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
        }
        val (_, priv) = loadKeysAndInsertIntoProps(samlProperties)

        AuthServer(
                samlSettings = samlProperties,
                privKey = priv,
                kafkaStreamsConfiguration = retrieveKafkaStreamsConfiguration(
                        config.kafka.servers,
                        AuthServiceDescription.name,
                        hostname,
                        port
                ),
                kafkaProducerConfiguration = retrieveKafkaProducerConfiguration(config.kafka.servers),
                config = config,
                hostname = "localhost"
        ).start()
    } else {
        when (args[0]) {
            "generate-db" -> {
                log.info("Generating database (intended for development only)")
                log.info("Connecting...")
                Database.connect(
                        url = config.database.url,
                        driver = config.database.driver,

                        user = config.database.username,
                        password = config.database.password
                )
                log.info("OK")

                log.info("Creating tables...")
                transaction {
                    create(Principals, RefreshTokens)
                }
                log.info("OK")
            }

            "create-user" -> {
                val producer = KafkaProducer<String, String>(retrieveKafkaProducerConfiguration(config.kafka.servers))
                val userEvents = producer.forStream(AuthStreams.UserUpdateStream)

                val console = System.console()
                val firstNames = console.readLine("First names: ")
                val lastName = console.readLine("Last name: ")

                val role = console.readLine("Role (String): ").let { Role.valueOf(it) }

                val email = console.readLine("Email: ")
                val password = String(console.readPassword("Password: "))

                val person = PersonUtils.createUserByPassword(firstNames, lastName, email, role, password)

                log.info("Creating user: ")
                log.info(person.toString())

                runBlocking {
                    userEvents.emit(person.id, UserEvent.Created(person.id, person))
                }

                log.info("OK")
            }

            "create-api-token" -> {
                val producer = KafkaProducer<String, String>(retrieveKafkaProducerConfiguration(config.kafka.servers))
                val userEvents = producer.forStream(AuthStreams.UserUpdateStream)
                val tokenEvents = producer.forStream(AuthStreams.RefreshTokenStream)

                val scanner = Scanner(System.`in`)
                print("Service name: ")
                val serviceName = scanner.nextLine()

                val token = UUID.randomUUID().toString()
                val principal = ServicePrincipal("_$serviceName", Role.SERVICE)

                runBlocking {
                    userEvents.emit(principal.id, UserEvent.Created(principal.id, principal))
                    val event = RefreshTokenEvent.Created(token, principal.id)
                    tokenEvents.emit(event.key, event)
                }

                log.info("Created a service user: ${principal.id}")
                log.info("Active refresh token: $token")
            }
        }
    }
}
