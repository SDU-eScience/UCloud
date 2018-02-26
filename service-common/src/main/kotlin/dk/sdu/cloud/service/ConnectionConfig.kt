package dk.sdu.cloud.service

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.client.ServiceDescription
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.service.ConnectionConfig")

data class KafkaHostConfig(
        val hostname: String,
        val port: Int = 9092
) {
    override fun toString(): String = "$hostname:$port"
}

data class DatabaseConfiguration(
        val url: String,
        val driver: String,
        val username: String,
        val password: String
)

data class KafkaConnectionConfig(val servers: List<KafkaHostConfig>)

data class RawServiceConnectionConfig(
        val hostname: String,
        val port: Int
)

data class ServiceConnectionConfig(
        val description: ServiceDescription,
        val hostname: String,
        val port: Int
)

data class RawConnectionConfig(
        // All private to encourage correct usage
        private val kafka: KafkaConnectionConfig,
        private val service: RawServiceConnectionConfig?,
        private val database: DatabaseConfiguration?
) {
    @get:JsonIgnore
    private var _processed: ConnectionConfig? = null

    @get:JsonIgnore
    val processed: ConnectionConfig get() =
        _processed ?: throw NullPointerException("Not yet configured. Use configure()")

    fun configure(description: ServiceDescription, defaultPort: Int = -1): ConnectionConfig {
        val existing = _processed
        if (existing != null) return existing

        val processedService = if (service == null) {
            val hostname = queryHostname()
            log.debug("Using default hostname/port")
            log.debug("  Hostname: $hostname")
            log.debug("  Port: $defaultPort")
            ServiceConnectionConfig(description, hostname, defaultPort)
        } else {
            ServiceConnectionConfig(description, service.hostname, service.port)
        }

        val result = ConnectionConfig(kafka, processedService, database)
        _processed = result
        return result
    }

    private fun queryHostname(): String {
        if (System.getProperty("os.name").startsWith("Windows")) {
            log.debug("Received hostname through COMPUTERNAME env variable")
            return System.getenv("COMPUTERNAME")
        } else {
            val env = System.getenv("HOSTNAME")
            if (env != null) {
                log.debug("Received hostname through HOSTNAME env variable")
                return env
            }

            log.debug("Attempting to retrieve hostname through hostname executable")
            return exec { command("hostname") }.lines().firstOrNull() ?:
                    throw IllegalStateException("Unable to retrieve hostname")
        }
    }

    private fun exec(builder: ProcessBuilder.() -> Unit): String {
        val process = ProcessBuilder().also(builder).start()
        process.waitFor()
        return process.inputStream.reader().readText()
    }
}

data class ConnectionConfig(
        val kafka: KafkaConnectionConfig,
        val service: ServiceConnectionConfig,
        val database: DatabaseConfiguration?
)