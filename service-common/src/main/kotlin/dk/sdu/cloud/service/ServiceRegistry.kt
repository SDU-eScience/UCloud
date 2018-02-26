package dk.sdu.cloud.service

import com.github.zafarkhaja.semver.Version
import com.orbitz.consul.Consul
import dk.sdu.cloud.client.HttpClient
import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

fun ServiceDescription.definition(): ServiceDefinition = ServiceDefinition(name, Version.valueOf(version))
fun ServiceDescription.instance(config: ConnectionConfig): ServiceInstance =
    ServiceInstance(definition(), config.service.hostname, config.service.port)

data class ServiceDefinition(val name: String, val version: Version)
data class ServiceInstance(val definition: ServiceDefinition, val hostname: String, val port: Int)

class ServiceRegistry(
    private val instance: ServiceInstance,
    consul: Consul = Consul.builder().build(),
    private val serviceCheckExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) {
    private val agent = consul.agentClient()
    private val health = consul.healthClient()

    private var isRegistered = false

    fun register(httpEndpoint: String? = HEALTH_URI, serviceCheckFunction: () -> Boolean = { true }) {
        if (isRegistered) throw IllegalStateException("Already registered at Consul!")

        // Must be unique, but should be reused (old entries will stick around in Consul but in a failed state
        // when TTL expires)
        val serviceId = "${instance.hostname}:${instance.port}-${instance.definition.name}"

        agent.register(
            instance.port,
            TTL_IN_SECONDS,
            instance.definition.name,
            serviceId,
            // Tags
            "api",
            VERSION_PREFIX + instance.definition.version.toStringNoMetadata()
        )

        serviceCheckExecutor.scheduleAtFixedRate({
            log.debug("Scheduling service check...")

            val result = try {
                serviceCheckFunction()
            } catch (ex: Exception) {
                log.warn("Caught exception while running service check!")
                log.warn(ex.stackTraceToString())
                false
            }

            log.debug("Result was $result")

            val serviceIsOkay: Boolean = run {
                if (!result) {
                    log.warn("Service check returned false. Setting state to critical!")
                    return@run false
                }

                if (httpEndpoint != null) {
                    // TODO FIXME HTTP IS HARDCODED
                    val status = runBlocking {
                        HttpClient.get("http://${instance.hostname}:${instance.port}$HEALTH_URI")
                    }

                    if (status.statusCode !in 200..299) {
                        log.warn("Health endpoint did not return a status code in range 200..299")
                        log.warn("${status.statusCode} - ${status.statusText}")
                        log.warn(status.responseBody)

                        agent.fail(serviceId)
                        return@run false
                    }
                }

                return@run true
            }

            if (serviceIsOkay) {
                agent.pass(serviceId)
            } else {
                agent.fail(serviceId)
            }
        }, 0L, CHECK_PERIOD_IN_SECONDS, TimeUnit.SECONDS)

        isRegistered = true
    }

    fun listServices(name: String): List<ServiceInstance> =
        health.getHealthyServiceInstances(name).response.mapNotNull {
            val version = it.service.tags.find { it.startsWith(VERSION_PREFIX) }?.let {
                try {
                    Version.valueOf(it.substringAfter(VERSION_PREFIX))
                } catch (ex: Exception) {
                    null
                }
            } ?: return@mapNotNull null

            ServiceInstance(ServiceDefinition(it.service.service, version), it.node.address, it.service.port)
        }

    fun listServices(name: String, versionExpression: String): List<ServiceInstance> {
        if (versionExpression.contains("-SNAPSHOT")) {
            throw IllegalArgumentException("Version metadata not allowed in version expression")
        }

        return listServices(name).filter { it.definition.version.satisfies(versionExpression) }
    }


    companion object {
        const val VERSION_PREFIX = "v."
        const val TTL_IN_SECONDS = 10L
        const val CHECK_PERIOD_IN_SECONDS = 5L

        private val log = LoggerFactory.getLogger(ServiceRegistry::class.java)
        private fun Version.toStringNoMetadata() = "$majorVersion.$minorVersion.$patchVersion"
    }
}