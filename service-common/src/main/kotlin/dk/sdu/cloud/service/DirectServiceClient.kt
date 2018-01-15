package dk.sdu.cloud.service

import com.github.zafarkhaja.semver.Version
import dk.sdu.cloud.client.CloudContext
import dk.sdu.cloud.client.PreparedRESTCall
import org.apache.zookeeper.ZooKeeper
import dk.sdu.cloud.client.ServiceDescription
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory
import java.net.ConnectException
import java.net.URL
import java.util.*

/**
 * A client intended for internal services.
 *
 * This client will connect directly to another internal service using the service registry. Services are chosen
 * from the registry in a random fashion and saved in a local cache. If the service eventually becomes unavailable
 * the [PreparedRESTCall] will call back to this client and it will automatically attempt to use a different service.
 *
 * In the event that no services are available a [ConnectException] will be thrown.
 *
 * By default this service will attempt to find services that are compatible with the version of the API
 * that it was compiled against. If a service A is compiled against a service B of version X.Y.Z then this client
 * will use an SemVer expression of "^X.Y.Z". From the NPM SemVer Expressions documentation:
 *
 * > Caret Ranges ^1.2.3 ^0.2.5 ^0.0.4
 * >
 * > Allows changes that do not modify the left-most non-zero digit in the [major, minor, patch] tuple. In other words,
 * > this allows patch and minor updates for versions 1.0.0 and above, patch updates for versions 0.X >=0.1.0, and no
 * > updates for versions 0.0.X.
 */
class DirectServiceClient(private val registry: ZooKeeper) : CloudContext {
    private val localCache = HashMap<String, URL>()
    private val versionCache = HashMap<String, String>()
    private val random = Random()

    override fun resolveEndpoint(call: PreparedRESTCall<*, *>): String {
        return resolveEndpoint(call.owner)
    }

    override fun resolveEndpoint(service: ServiceDescription): String {
        return findServiceViaCache(service)
    }

    override fun tryReconfigurationOnConnectException(call: PreparedRESTCall<*, *>, ex: ConnectException): Boolean {
        return try {
            findServiceViaCache(call.owner)
            true
        } catch (ex: ConnectException) {
            false
        }
    }

    /**
     * Overrides the version expression used to identify services.
     *
     * By default a service's [ServiceDescription] is used to identify the minimum version required. The default
     * version expression (for a version X.Y.Z) is "^X.Y.Z".
     */
    fun overrideServiceVersionPreference(serviceName: String, versionExpression: String) {
        versionCache[serviceName] = versionExpression
    }

    private fun findServiceViaCache(description: ServiceDescription) =
            localCache.computeIfAbsent(description.name) {
                runBlocking {
                    findService(description)
                }
            }.toString().also {
                log.debug("findServiceCache($description) = $it")
            }

    private suspend fun findService(service: ServiceDescription): URL {
        log.debug("Locating service: $service")
        val versionExpression = versionCache.computeIfAbsent(service.name) {
            val parsed = Version.valueOf(service.version)
            "^${parsed.majorVersion}.${parsed.minorVersion}.${parsed.patchVersion}"
        }

        val services = registry.listServicesWithStatus(service.name, versionExpression)
                .values
                .firstOrNull()
                ?.filter { it.status == ServiceStatus.READY }
                ?.takeIf { it.isNotEmpty() }
                ?: throw ConnectException("No services available")

        log.debug("Found the following service: $services")
        val resolvedService = services[random.nextInt(services.size)]
        log.debug("Using $resolvedService")
        return URL("http://${resolvedService.instance.hostname}:${resolvedService.instance.port}")
    }

    companion object {
        private val log = LoggerFactory.getLogger(DirectServiceClient::class.java)
    }
}