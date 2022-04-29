package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import kotlin.random.nextULong


class EnvoyConfigurationService(
    private val configDir: String,
) {
    private sealed class ConfigurationMessage {
        data class NewCluster(
            val route: EnvoyRoute,
            val cluster: EnvoyCluster?
        ) : ConfigurationMessage() {
            init {
                require(cluster == null || route.cluster == cluster.name)
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

    fun start(port: Int?) {
        writeConfigurationFile(port ?: 8889)

        val logFile = "/tmp/envoy.log"

        // TODO We probably cannot depend on this being allowed to download envoy for us. We need an alternative for
        //  people who don't what this.
        val envoyPid = startProcess(
            args = listOf(
                "/usr/local/bin/getenvoy",
                "run",
                "--config-path",
                "$configDir/$configFile"
            ),

            envs = listOf("ENVOY_VERSION=1.19.0"),

            createStreams = {
                val devnull = NativeFile.open("/dev/null", readOnly = false)
                val logFile = NativeFile.open(logFile, readOnly = false, truncateIfNeeded = false)
                ProcessStreams(devnull.fd, logFile.fd, logFile.fd)
            }
        )

        val job = ProcessingScope.launch {
            val entries = hashMapOf<String, Pair<EnvoyRoute, EnvoyCluster?>>()
            run {
                val id = "_UCloud"
                entries[id] = Pair(
                    EnvoyRoute.Standard(null, id),
                    EnvoyCluster.create(id, "127.0.0.1", UCLOUD_IM_PORT)
                )

                val routes = entries.values.map { it.first }
                val clusters = entries.values.mapNotNull { it.second }
                configure(configDir, routes, clusters)
            }

            while (isActive) {
                val nextMessage = channel.receiveCatching().getOrNull() ?: break
                when (nextMessage) {
                    is ConfigurationMessage.NewCluster -> {
                        val keySuffix = nextMessage.route::class.simpleName
                        entries[nextMessage.route.cluster + keySuffix] = Pair(nextMessage.route, nextMessage.cluster)
                    }
                }

                val routes = entries.values.map { it.first }
                val clusters = entries.values.mapNotNull { it.second }
                configure(configDir, routes, clusters)
            }
        }

        ProcessWatcher.addWatchBlocking(envoyPid) { statusCode ->
            log.warn("Envoy has died unexpectedly! It exited with $statusCode.")
            log.warn("You might be able to read additional information from: $logFile")
            log.warn("We will attempt to restart Envoy now!")
            job.cancel()
            start(port)
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

    fun requestConfiguration(route: EnvoyRoute, cluster: EnvoyCluster?) {
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
        private var wsRoutes: MutableList<EnvoyRoute> = mutableListOf()

        private fun configure(configDir: String, routes: List<EnvoyRoute>, clusters: List<EnvoyCluster>) {

            //TODO: refactor later, also handle wsRoutes lifecycle
            wsRoutes.addAll(routes.filter{ r -> r is EnvoyRoute.ShellSession})
            var allRoutes: MutableList<EnvoyRoute> = mutableListOf()
            allRoutes.addAll(routes)
            allRoutes.addAll(wsRoutes)

            val tempRouteFile = "$configDir/$tempPrefix$rdsFile"
            NativeFile.open(tempRouteFile, readOnly = false).writeText(
                defaultMapper.encodeToString(EnvoyResources(listOf(EnvoyRouteConfiguration.create(allRoutes))))
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


sealed class EnvoyRoute {
    abstract val cluster: String

    data class Standard(
        val ucloudIdentity: String?,
        override val cluster: String
    ) : EnvoyRoute()

    data class ShellSession(
        val identifier: String,
        val providerId: String,
        override val cluster: String
    ) : EnvoyRoute()

    data class DownloadSession(
        val identifier: String,
        val providerId: String,
        override val cluster: String
    ) : EnvoyRoute()

    data class UploadSession(
        val identifier: String,
        val providerId: String,
        override val cluster: String
    ) : EnvoyRoute()
}


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

            val sortedRoutes = routes.sortedBy {
                // NOTE(Dan): We must ensure that the sessions are routed with a higher priority, otherwise the
                // traffic will always go to the wrong route.
                when (it) {
                    is EnvoyRoute.UploadSession -> 1
                    is EnvoyRoute.DownloadSession -> 1
                    is EnvoyRoute.ShellSession -> 1
                    is EnvoyRoute.Standard -> 2
                }
            }

            return EnvoyRouteConfiguration(
                virtualHosts = listOf(
                    JsonObject(
                        "name" to JsonPrimitive("local_route"),
                        "domains" to JsonArray(JsonPrimitive("*")),
                        "routes" to JsonArray(
                            sortedRoutes.map { route ->
                                val standardRouteConfig = JsonObject(
                                    "cluster" to JsonPrimitive(route.cluster),
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

                                when (route) {
                                    is EnvoyRoute.Standard -> {
                                        val ucloudIdentity = route.ucloudIdentity
                                        val cluster = route.cluster
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
                                                                put("exact_match", JsonPrimitive(
                                                                    base64Encode(ucloudIdentity.encodeToByteArray())
                                                                ))
                                                            }
                                                        }
                                                    )
                                                )
                                            ),
                                            "route" to standardRouteConfig
                                        )
                                    }

                                    is EnvoyRoute.ShellSession -> {
                                        JsonObject(
                                            "match" to JsonObject(
                                                "path" to JsonPrimitive("/ucloud/${route.providerId}/websocket"),
                                                "query_parameters" to JsonArray(
                                                    JsonObject(
                                                        "name" to JsonPrimitive("session"),
                                                        "string_match" to JsonObject(
                                                            "exact" to JsonPrimitive(route.identifier)
                                                        )
                                                    )
                                                )
                                            ),
                                            "route" to standardRouteConfig,
                                        )
                                    }

                                    is EnvoyRoute.DownloadSession -> {
                                        JsonObject(
                                            "match" to JsonObject(
                                                "path" to JsonPrimitive(FileController.downloadPath(route.providerId)),
                                                "query_parameters" to JsonArray(
                                                    JsonObject(
                                                        "name" to JsonPrimitive("token"),
                                                        "string_match" to JsonObject(
                                                            "exact" to JsonPrimitive(route.identifier)
                                                        )
                                                    )
                                                )
                                            ),
                                            "route" to standardRouteConfig,
                                        )
                                    }

                                    is EnvoyRoute.UploadSession -> {
                                        JsonObject(
                                            "match" to JsonObject(
                                                "path" to JsonPrimitive(FileController.uploadPath(route.providerId)),
                                                "headers" to JsonArray(
                                                    JsonObject(
                                                        "name" to JsonPrimitive("Chunked-Upload-Token"),
                                                        "string_match" to JsonObject(
                                                            "exact" to JsonPrimitive(route.identifier)
                                                        )
                                                    )
                                                )
                                            ),
                                            "route" to standardRouteConfig,
                                        )
                                    }
                                }
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
