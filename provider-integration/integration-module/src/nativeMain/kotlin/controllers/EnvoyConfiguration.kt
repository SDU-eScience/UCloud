package dk.sdu.cloud.controllers

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.freeze
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.receiveOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.random.Random
import kotlin.random.nextULong

class EnvoyConfigurationService(
    private val configDir: String,
) {
    private sealed class ConfigurationMessage {
        data class NewCluster(val route: EnvoyRoute, val cluster: EnvoyCluster) : ConfigurationMessage() {
            init {
                require(route.cluster == cluster.name)
            }
        }
    }

    private val channel = Channel<ConfigurationMessage>(Channel.BUFFERED)

    init {
        if (!fileExists("$configDir/$rdsFile") || !fileExists("$configDir/$clustersFile")) {
            NativeFile.open("$configDir/$rdsFile", readOnly = false).writeText("{}")
            NativeFile.open("$configDir/$clustersFile", readOnly = false).writeText("{}")
            configure(configDir, emptyList(), emptyList())
        }
    }

    fun start(port: Int?): Worker {
        writeConfigurationFile(port ?: 8889)

        // TODO We probably cannot depend on this being allowed to download envoy for us. We need an alternative for
        //  people who don't what this.
        startProcess(
            listOf(
                "/usr/local/bin/getenvoy",
                "run",
                "standard:1.18.2",
                "--",
                "--config-path",
                "$configDir/$configFile"
            ),
            createStreams = {
                val devnull = NativeFile.open("/dev/null", readOnly = false)
                val logFile = NativeFile.open("/tmp/envoy.log", readOnly = false, truncateIfNeeded = true)
                ProcessStreams(devnull.fd, logFile.fd, logFile.fd)
            }
        )

        return Worker.start(name = "Envoy configuration worker")
            .also {
                it.execute(TransferMode.UNSAFE, { Pair(configDir, channel).freeze() }) { (configDir, channel) ->
                    runBlocking {
                        val entries = hashMapOf<String, Pair<EnvoyRoute, EnvoyCluster>>()
                        run {
                            val id = "_UCloud"
                            entries[id] = Pair(
                                EnvoyRoute(null, id),
                                EnvoyCluster.create(id, "127.0.0.1", UCLOUD_IM_PORT)
                            )

                            val routes = entries.values.map { it.first }
                            val clusters = entries.values.map { it.second }
                            configure(configDir, routes, clusters)
                        }

                        while (isActive) {
                            val nextMessage = channel.receiveOrNull() ?: break
                            when (nextMessage) {
                                is ConfigurationMessage.NewCluster -> {
                                    entries[nextMessage.route.cluster] = Pair(nextMessage.route, nextMessage.cluster)
                                }
                            }

                            val routes = entries.values.map { it.first }
                            val clusters = entries.values.map { it.second }
                            configure(configDir, routes, clusters)
                        }
                    }
                }
            }
    }

    private fun writeConfigurationFile(port: Int) {
        NativeFile.open("$configDir/$configFile", readOnly = false).writeText(
            //language=YAML
            """
dynamic_resources:
  cds_config:
    path: $configDir/$clustersFile

node:
  cluster: ucloudim_cluster
  id: ucloudim_stack

admin:
  access_log_path: "/dev/stdout"

static_resources:
  listeners:
    - address:
        socket_address:
          address: 0.0.0.0
          port_value: $port
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                codec_type: HTTP1
                http_filters:
                - name: envoy.filters.http.router
                stat_prefix: ingress_http
                rds:
                  route_config_name: local_route
                  config_source:
                    path: $configDir/$rdsFile
  clusters: []
          """
        )
    }

    fun requestConfiguration(route: EnvoyRoute, cluster: EnvoyCluster) {
        runBlocking {
            channel.send(ConfigurationMessage.NewCluster(route, cluster))
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val tempPrefix = "temp-"
        private const val rdsFile = "rds.yaml"
        private const val clustersFile = "clusters.yaml"
        private const val configFile = "config.yaml"

        private fun configure(configDir: String, routes: List<EnvoyRoute>, clusters: List<EnvoyCluster>) {
            val tempRouteFile = "$configDir/$tempPrefix$rdsFile"
            NativeFile.open(tempRouteFile, readOnly = false).writeText(
                defaultMapper.encodeToString(EnvoyResources(listOf(EnvoyRouteConfiguration.create(routes))))
            )

            val tempClusterFile = "$configDir/$tempPrefix$clustersFile"
            NativeFile.open(tempClusterFile, readOnly = false).writeText(
                defaultMapper.encodeToString(EnvoyResources(clusters))
            )

            renameFile(tempRouteFile, "$configDir/$rdsFile", 0u)
            renameFile(tempClusterFile, "$configDir/$clustersFile", 0u)
        }
    }
}

@Serializable
data class EnvoyResources<T>(
    val resources: List<T>,

    @SerialName("version_info")
    val version: String = buildString {
        append(Random.nextULong().toString(16))
        append(Random.nextULong().toString(16))
        append(Random.nextULong().toString(16))
    }
)

data class EnvoyRoute(
    val ucloudIdentity: String?,
    val cluster: String
)

@Serializable
class EnvoyRouteConfiguration(
    @SerialName("@type")
    val resourceType: String = "type.googleapis.com/envoy.config.route.v3.RouteConfiguration",
    val name: String = "local_route",
    @SerialName("virtual_hosts")
    val virtualHosts: List<JsonObject>,
) {
    companion object {
        fun create(routes: List<EnvoyRoute>): EnvoyRouteConfiguration {
            return EnvoyRouteConfiguration(
                virtualHosts = listOf(
                    JsonObject(
                        "name" to JsonPrimitive("local_route"),
                        "domains" to JsonArray(JsonPrimitive("*")),
                        "routes" to JsonArray(
                            routes.map { (ucloudIdentity, cluster) ->
                                JsonObject(
                                    "match" to JsonObject(
                                        "prefix" to JsonPrimitive("/"),
                                        "headers" to JsonArray(
                                            JsonObject(
                                                buildMap {
                                                    put("name", JsonPrimitive("UCloud-Username"))
                                                    if (ucloudIdentity == null) {
                                                        put("invert_match", JsonPrimitive(true))
                                                        put("present_match", JsonPrimitive(true))
                                                    } else {
                                                        put("exact_match", JsonPrimitive(ucloudIdentity))
                                                    }
                                                }
                                            )
                                        )
                                    ),
                                    "route" to JsonObject(
                                        "cluster" to JsonPrimitive(cluster),
                                        "timeout" to JsonObject(
                                            "seconds" to JsonPrimitive(0)
                                        ),
                                        "upgrade_configs" to JsonArray(
                                            JsonObject(
                                                "upgrade_type" to JsonPrimitive("websocket"),
                                                "enabled" to JsonPrimitive(true)
                                            )
                                        )
                                    )
                                )
                            } + JsonObject(
                                "match" to JsonObject(
                                    "prefix" to JsonPrimitive(""),
                                ),
                                "direct_response" to JsonObject(
                                    "status" to JsonPrimitive(449)
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

@Serializable
data class EnvoyCluster(
    val name: String,

    @SerialName("connect_timeout")
    val connectTimeout: String = "0.25s",

    @SerialName("@type")
    val resourceType: String = "type.googleapis.com/envoy.config.cluster.v3.Cluster",

    @SerialName("lb_policy")
    val lbPolicy: String = "ROUND_ROBIN",

    val type: String = "STATIC",


    @SerialName("upstream_connection_options")
    val upstreamConnectionOptions: JsonObject = JsonObject(
        mapOf(
            "tcp_keepalive" to JsonObject(emptyMap())
        )
    ),

    @SerialName("load_assignment")
    val loadAssignment: JsonObject
) {
    companion object {
        fun create(
            name: String,
            address: String,
            port: Int,
        ): EnvoyCluster {
            return EnvoyCluster(
                name,
                loadAssignment = JsonObject(
                    "cluster_name" to JsonPrimitive(name),
                    "endpoints" to JsonArray(
                        JsonObject(
                            "lb_endpoints" to JsonArray(
                                JsonObject(
                                    "endpoint" to JsonObject(
                                        "address" to JsonObject(
                                            "socket_address" to JsonObject(
                                                "address" to JsonPrimitive(address),
                                                "port_value" to JsonPrimitive(port)
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

private fun JsonObject(vararg pairs: Pair<String, JsonElement>): JsonObject {
    return JsonObject(mapOf(*pairs))
}

private fun JsonArray(vararg elements: JsonElement): JsonArray {
    return JsonArray(listOf(*elements))
}
