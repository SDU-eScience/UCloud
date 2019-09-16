package dk.sdu.cloud.app.kubernetes.services

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.Loggable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.util.*

private val yamlFactory = YAMLFactory()
private val yamlMapper = ObjectMapper(yamlFactory).registerKotlinModule()

class EnvoyConfigurationService(
    private val routeConfiguration: File,
    private val clusterConfiguration: File
) {
    private val lock = Mutex()

    init {
        require(routeConfiguration.extension == "yaml")
        require(clusterConfiguration.extension == "yaml")

        log.debug("Envoy is configured at ${routeConfiguration.absolutePath} and ${clusterConfiguration.absolutePath}")
        if (!routeConfiguration.exists() || !clusterConfiguration.exists()) {
            runBlocking {
                lock.withLock {
                    log.debug("Creating empty resources")
                    configure(EnvoyResources(emptyList()), EnvoyResources(emptyList()))
                }
            }
        }
    }

    suspend fun configure(routes: EnvoyResources<EnvoyRoute>, clusters: EnvoyResources<EnvoyCluster>) {
        lock.withLock {
            log.debug("Reconfiguring envoy with ${routes.resources.size} routes and ${clusters.resources.size} clusters")
            val tempRouteFile = Files.createTempFile("routes", ".yml").toFile().also {
                it.writeText(yamlMapper.writeValueAsString(routes))
            }

            val tempClusterFile = Files.createTempFile("clusters", ".yml").toFile().also {
                it.writeText(yamlMapper.writeValueAsString(clusters))
            }

            tempRouteFile.renameTo(routeConfiguration)
            tempClusterFile.renameTo(clusterConfiguration)
        }
    }

    companion object : Loggable {
        override val log = logger()
    }
}

data class EnvoyResources<T>(
    val resources: List<T>,

    @get:JsonProperty("version_info")
    val version: String = UUID.randomUUID().toString()
)

class EnvoyRoute(
    @get:JsonIgnore
    val domain: String,

    @get:JsonIgnore
    val cluster: String
) {
    @get:JsonProperty("@type")
    val resourceType = "type.googleapis.com/envoy.api.v2.RouteConfiguration"

    val name: String = "local_route"

    @get:JsonProperty("virtual_hosts")
    val virtualHosts = listOf(
        mapOf<String, Any?>(
            "name" to name,
            "domains" to listOf(domain),
            "routes" to listOf(
                mapOf<String, Any?>(
                    "match" to mapOf(
                        "prefix" to "/"
                    ),
                    "route" to mapOf(
                        "cluster" to cluster
                    )
                )
            )
        )
    )
}

class EnvoyCluster(
    val name: String,

    @get:JsonIgnore
    val address: String,

    @get:JsonIgnore
    val port: Int,

    @get:JsonProperty("connect_timeout")
    val connectTimeout: String = "0.25s"
) {
    @get:JsonProperty("@type")
    val resourceType = "type.googleapis.com/envoy.api.v2.Cluster"

    @get:JsonProperty("lb_policy")
    val lbPolicy = "ROUND_ROBIN"

    @get:JsonProperty("upstream_connection_options")
    val upstreamConnectionOptions = mapOf(
        "tcp_keepalive" to emptyMap<String, Any?>()
    )

    val type = "STATIC"

    @get:JsonProperty("load_assignment")
    val loadAssignment = mapOf<String, Any?>(
        "cluster_name" to name,
        "endpoints" to listOf(
            mapOf<String, Any?>(
                "lb_endpoints" to listOf(
                    mapOf<String, Any?>(
                        "endpoint" to mapOf<String, Any?>(
                            "address" to mapOf<String, Any?>(
                                "socket_address" to mapOf<String, Any?>(
                                    "address" to address,
                                    "port_value" to port
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}
