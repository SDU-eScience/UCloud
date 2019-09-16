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
import java.nio.file.StandardCopyOption
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
                routeConfiguration.writeText("{}")
                clusterConfiguration.writeText("{}")
                log.debug("Creating empty resources")
                configure(emptyList(), emptyList())
            }
        }
    }

    suspend fun configure(routes: List<EnvoyRoute>, clusters: List<EnvoyCluster>) {
        lock.withLock {
            log.debug("Reconfiguring envoy with ${routes.size} routes and ${clusters.size} clusters")
            val tempRouteFile = File("./envoy/temp-route-file.yaml").also {
                it.writeText(yamlMapper.writeValueAsString(EnvoyResources(listOf(EnvoyRouteConfiguration(routes)))))
            }

            val tempClusterFile = File("./envoy/temp-cluster-file.yaml").also {
                it.writeText(yamlMapper.writeValueAsString(EnvoyResources(clusters)))
            }

            Files.move(tempRouteFile.toPath(), routeConfiguration.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(tempClusterFile.toPath(), clusterConfiguration.toPath(), StandardCopyOption.REPLACE_EXISTING)
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

data class EnvoyRoute(
    val domain: String,
    val cluster: String
)

class EnvoyRouteConfiguration(routes: List<EnvoyRoute>) {
    @get:JsonProperty("@type")
    val resourceType = "type.googleapis.com/envoy.api.v2.RouteConfiguration"

    val name: String = "local_route"

    @get:JsonProperty("virtual_hosts")
    val virtualHosts = routes.map { (domain, cluster) ->
        mapOf<String, Any?>(
            "name" to name,
            "domains" to listOf(domain),
            "routes" to listOf(
                mapOf<String, Any?>(
                    "match" to mapOf(
                        "prefix" to "/"
                    ),
                    "route" to mapOf(
                        "cluster" to cluster,
                        "upgrade_configs" to listOf(
                            mapOf(
                                "upgrade_type" to "websocket",
                                "enabled" to true
                            )
                        )
                    )
                )
            )
        )
    }
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
