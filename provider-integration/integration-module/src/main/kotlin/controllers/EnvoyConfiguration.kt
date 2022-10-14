package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.UCLOUD_IM_PORT
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import kotlin.random.*

class EnvoyConfigurationService(
    private val executable: String?,
    private val configDir: String,
    private val useUCloudUsernameHeader: Boolean,
    private val logDirectory: String,
    private val providerId: String? = null,
    private val downstreamTls: Boolean
) {
    constructor(config: VerifiedConfig) : this(
        config.server.envoy.executable,
        config.server.envoy.directory,
        config.core.launchRealUserInstances,
        config.core.logs.directory,
        config.core.providerId,
        config.server.envoy.downstreamTls,
    )

    private sealed class ConfigurationMessage {
        data class ClusterUp(
            val cluster: EnvoyCluster
        ) : ConfigurationMessage()

        data class ClusterDown(
            val cluster: EnvoyCluster
        ) : ConfigurationMessage()

        data class RouteUp(
            val route: EnvoyRoute
        ) : ConfigurationMessage()

        data class RouteDown(
            val route: EnvoyRoute
        ) : ConfigurationMessage()
    }

    private val channel = Channel<ConfigurationMessage>(Channel.BUFFERED)

    init {
        val cfg = File(configDir)
        val rds = File(cfg, rdsFile)
        val clusters = File(cfg, clustersFile)

        if (!cfg.exists()) cfg.mkdirs()
        if (!rds.exists() || !clusters.exists()) {
            rds.writeText("{}")
            clusters.writeText("{}")
            configure(emptyList(), emptyList())
        }
    }

    fun start(
        port: Int?,
        initClusters: Collection<EnvoyCluster>? = null,
        initRoutes: Collection<EnvoyRoute>? = null,
    ) {
        require((initClusters == null && initRoutes == null) || (initClusters != null && initRoutes != null))

        writeConfigurationFile(port ?: 8889)

        val logFile = "/${logDirectory}/envoy.log"

        // TODO We probably cannot depend on this being allowed to download envoy for us. We need an alternative for
        //  people who don't what this.
        val envoyProcess = startProcess(
            args = listOf(
                executable ?: "/usr/local/bin/getenvoy",
                "run",
                "--config-path",
                "$configDir/$configFile"
            ),
            envs = listOf("ENVOY_VERSION=1.23.0"),
            attachStdout = false,
            attachStderr = false,
            attachStdin = false,
        )

        val routes = hashSetOf<EnvoyRoute>()
        val clusters = hashMapOf<String, EnvoyCluster>()

        val job = ProcessingScope.launch {
            if (initClusters != null && initRoutes != null) {
                routes.addAll(initRoutes)
                for (cluster in initClusters) clusters[cluster.name] = cluster
            } else {
                routes.add(EnvoyRoute.Standard(null, IM_SERVER_CLUSTER))
                clusters[IM_SERVER_CLUSTER] = EnvoyCluster.create(IM_SERVER_CLUSTER, "127.0.0.1", UCLOUD_IM_PORT)
                if (providerId != null) routes.add(EnvoyRoute.WebAuthorizeSession(providerId, IM_SERVER_CLUSTER))
            }

            configure(routes, clusters.values)

            while (isActive) {
                val nextMessage = channel.receiveCatching().getOrNull() ?: break
                when (nextMessage) {
                    is ConfigurationMessage.ClusterDown -> {
                        clusters.remove(nextMessage.cluster.name)
                        routes.removeIf { it.cluster == nextMessage.cluster.name }
                    }

                    is ConfigurationMessage.ClusterUp -> {
                        clusters[nextMessage.cluster.name] = nextMessage.cluster
                    }

                    is ConfigurationMessage.RouteDown -> {
                        routes.remove(nextMessage.route)
                    }

                    is ConfigurationMessage.RouteUp -> {
                        routes.add(nextMessage.route)
                    }
                }

                configure(routes, clusters.values)
            }
        }

        ProcessWatcher.addWatchBlocking(envoyProcess) { statusCode ->
            log.warn("Envoy has died unexpectedly! It exited with $statusCode.")
            log.warn("You might be able to read additional information from: $logFile")
            log.warn("We will attempt to restart Envoy now!")
            job.cancel()
            while (job.isActive) delay(50)
            start(port, clusters.values, routes)
        }
    }

    suspend fun requestConfiguration(route: EnvoyRoute, cluster: EnvoyCluster? = null) {
        channel.send(ConfigurationMessage.RouteUp(route))
        if (cluster != null) channel.send(ConfigurationMessage.ClusterUp(cluster))
    }

    suspend fun requestClusterUp(cluster: EnvoyCluster) {
        channel.send(ConfigurationMessage.ClusterUp(cluster))
    }

    suspend fun requestClusterDown(cluster: EnvoyCluster) {
        channel.send(ConfigurationMessage.ClusterDown(cluster))
    }

    suspend fun requestRouteUp(route: EnvoyRoute) {
        channel.send(ConfigurationMessage.RouteUp(route))
    }

    suspend fun requestRouteDown(route: EnvoyRoute) {
        channel.send(ConfigurationMessage.RouteDown(route))
    }

    private fun configure(routes: Collection<EnvoyRoute>, clusters: Collection<EnvoyCluster>) {
        val tempRouteFile = "$configDir/$tempPrefix$rdsFile"
        NativeFile.open(tempRouteFile, readOnly = false).writeText(
            defaultMapper.encodeToString(
                EnvoyResources.serializer(EnvoyRouteConfiguration.serializer()),
                EnvoyResources(listOf(EnvoyRouteConfiguration.create(routes, useUCloudUsernameHeader)))
            )
        )

        val tempClusterFile = "$configDir/$tempPrefix$clustersFile"
        NativeFile.open(tempClusterFile, readOnly = false).writeText(
            defaultMapper.encodeToString(
                EnvoyResources.serializer(EnvoyCluster.serializer()),
                EnvoyResources(clusters)
            )
        )

        renameFile(tempRouteFile, "$configDir/$rdsFile")
        renameFile(tempClusterFile, "$configDir/$clustersFile")
    }

    private fun writeConfigurationFile(port: Int) {
        val tlsDefaultDownstream = if (!downstreamTls) "" else """
          transport_socket:
            name: envoy.transport_sockets.tls
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.transport_sockets.tls.v3.DownstreamTlsContext
              common_tls_context:
                tls_params:
                  tls_minimum_protocol_version: "TLSv1_2"
                  tls_maximum_protocol_version: "TLSv1_3"
                  cipher_suites: "ECDHE-ECDSA-AES256-GCM-SHA384"
                  # curve X25519 available in openssl builds
                  ecdh_curves: "P-256"
                tls_certificates:
                - certificate_chain:
                    filename: $configDir/certs/public.crt
                  private_key:
                    filename: $configDir/certs/private.key

"""

        if (downstreamTls) {
            File(configDir, "certs").mkdir()

            // NOTE(Roman): Generate private key and generate X509 cert
            startProcess(
                args = listOf(
                    "/bin/openssl",
                    "ecparam",
                    "-name",
                    "prime256v1",
                    "-genkey",
                    "-noout",
                    "-out",
                    "$configDir/certs/private.key"
                ),
                attachStdout = false,
                attachStderr = false,
                attachStdin = false,
            )

            startProcess(
                args = listOf(
                    "/bin/openssl",
                    "req",
                    "-new",
                    "-x509",
                    "-key",
                    "$configDir/certs/private.key",
                    "-out",
                    "$configDir/certs/public.crt",
                    "-days",
                    "360",
                    "-subj",
                    "/CN=integration-module/O=integration-module"
                ),
                attachStdout = false,
                attachStderr = false,
                attachStdin = false,
            )
        }

        NativeFile.open("$configDir/$configFile", readOnly = false, truncateIfNeeded = true).writeText(
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
  
layered_runtime:
  layers:
  - name: static_layer_0
    static_layer:
      envoy:
        resource_limits:
          listener:
            example_listener_name:
              connection_limit: 10000
      overload:
        global_downstream_max_connections: 50000

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
                - name: envoy.filters.http.ext_authz 
                  typed_config:
                    "@type": type.googleapis.com/envoy.extensions.filters.http.ext_authz.v3.ExtAuthz
                    failure_mode_allow: false
                    http_service:
                      path_prefix: /app-authorize-request
                      server_uri:
                        uri: http://0.0.0.0/
                        cluster: ext-authz
                        timeout: 0.25s
                      authorization_request:
                        allowed_headers:
                          patterns:
                            - exact: Cookie
                              ignore_case: true
                      authorization_response:
                        allowed_upstream_headers:
                          patterns:
                            - exact: Cookie
                              ignore_case: true
                - name: envoy.filters.http.router
                  typed_config:
                    "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
                stat_prefix: ingress_http
                rds:
                  route_config_name: local_route
                  config_source:
                    path: $configDir/$rdsFile

$tlsDefaultDownstream


  clusters:
  - name: ext-authz
    connect_timeout: 0.25s
    type: STATIC
    lb_policy: ROUND_ROBIN
    upstream_connection_options:
      tcp_keepalive: {}
    load_assignment:
      cluster_name: ext-authz
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: 127.0.0.1
                port_value: 42000
          """
        )
    }

    companion object : Loggable {
        override val log = logger()
        private const val tempPrefix = "temp-"
        private const val rdsFile = "rds.yaml"
        private const val clustersFile = "clusters.yaml"
        private const val configFile = "config.yaml"

        const val IM_SERVER_CLUSTER = "_UCloud"
    }
}

@Serializable
data class EnvoyResources<T>(
    val resources: Collection<T>,

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

    data class WebIngressSession(
        val identifier: String,
        val domain: String,
        val isAuthorizationEnabled: Boolean,
        override val cluster: String,
    ) : EnvoyRoute()

    data class WebAuthorizeSession(
        val providerId: String,
        override val cluster: String,
    ) : EnvoyRoute()

    data class VncSession(
        val identifier: String,
        val providerId: String,
        override val cluster: String,
    ) : EnvoyRoute()
}


@Serializable
class EnvoyRouteConfiguration(
    @SerialName("@type")
    @Suppress("unused")
    val resourceType: String = "type.googleapis.com/envoy.config.route.v3.RouteConfiguration",
    val name: String = "local_route",
    @SerialName("virtual_hosts")
    @Suppress("unused")
    val virtualHosts: List<JsonObject>,
) {
    companion object {
        fun create(routes: Collection<EnvoyRoute>, useUCloudUsernameHeader: Boolean): EnvoyRouteConfiguration {
            val sortedRoutes = routes.sortedBy {
                // NOTE(Dan): We must ensure that the sessions are routed with a higher priority, otherwise the
                // traffic will always go to the wrong route.
                when (it) {
                    is EnvoyRoute.VncSession -> 5

                    is EnvoyRoute.WebAuthorizeSession -> 5
                    is EnvoyRoute.WebIngressSession -> 6

                    is EnvoyRoute.Standard -> {
                        if (it.ucloudIdentity == null) 11
                        else 10
                    }
                }
            }

            // NOTE(Dan): Annoyingly we cannot enable it per route, we have to disable it for all
            // other routes.
            val disableAppAuthorization = "typed_per_filter_config" to JsonObject(
                "envoy.filters.http.ext_authz" to JsonObject(
                    "@type" to JsonPrimitive("type.googleapis.com/envoy.extensions.filters.http.ext_authz.v3.ExtAuthzPerRoute"),
                    "disabled" to JsonPrimitive(true)
                )
            )

            return EnvoyRouteConfiguration(
                virtualHosts = listOf(
                    JsonObject(
                        "name" to JsonPrimitive("local_route"),
                        "domains" to JsonArray(JsonPrimitive("*")),
                        "routes" to JsonArray(
                            sortedRoutes.flatMap { route ->
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
                                        buildList {
                                            add(
                                                JsonObject(
                                                    "match" to JsonObject(
                                                        buildMap {
                                                            put("prefix", JsonPrimitive("/"))
                                                            if (useUCloudUsernameHeader) {
                                                                put("headers", JsonArray(
                                                                    JsonObject(
                                                                        buildMap {
                                                                            put(
                                                                                "name",
                                                                                JsonPrimitive("UCloud-Username")
                                                                            )
                                                                            if (ucloudIdentity == null) {
                                                                                put(
                                                                                    "invert_match",
                                                                                    JsonPrimitive(true)
                                                                                )
                                                                                put(
                                                                                    "present_match",
                                                                                    JsonPrimitive(true)
                                                                                )
                                                                            } else {
                                                                                put(
                                                                                    "exact_match", JsonPrimitive(
                                                                                        base64Encode(ucloudIdentity.encodeToByteArray())
                                                                                    )
                                                                                )
                                                                            }
                                                                        }
                                                                    )
                                                                ))
                                                            }
                                                        }
                                                    ),
                                                    "route" to standardRouteConfig,
                                                    disableAppAuthorization
                                                )
                                            )

                                            if (useUCloudUsernameHeader && ucloudIdentity != null) {
                                                add(
                                                    JsonObject(
                                                        "match" to JsonObject(
                                                            buildMap {
                                                                put("prefix", JsonPrimitive("/"))
                                                                put("query_parameters", JsonArray(
                                                                    JsonObject(
                                                                        mapOf(
                                                                            "name" to JsonPrimitive("usernameHint"),
                                                                            "string_match" to JsonObject(
                                                                                mapOf(
                                                                                    "exact" to JsonPrimitive(base64Encode(ucloudIdentity.encodeToByteArray()))
                                                                                )
                                                                            )
                                                                        )
                                                                    )
                                                                ))
                                                            }
                                                        ),
                                                        "route" to standardRouteConfig,
                                                        disableAppAuthorization
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    is EnvoyRoute.VncSession -> {
                                        listOf(
                                            JsonObject(
                                                "match" to JsonObject(
                                                    "path" to JsonPrimitive("/ucloud/${route.providerId}/vnc"),
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
                                                disableAppAuthorization
                                            )
                                        )
                                    }

                                    is EnvoyRoute.WebAuthorizeSession -> {
                                        listOf(
                                            JsonObject(
                                                "match" to JsonObject(
                                                    "prefix" to JsonPrimitive("/ucloud/${route.providerId}/authorize-app"),
                                                ),
                                                "route" to standardRouteConfig,
                                                disableAppAuthorization
                                            )
                                        )
                                    }

                                    is EnvoyRoute.WebIngressSession -> {
                                        listOf(
                                            JsonObject(
                                                buildMap {
                                                    put(
                                                        "match", JsonObject(
                                                            "prefix" to JsonPrimitive("/"),
                                                            "headers" to JsonArray(
                                                                JsonObject(
                                                                    "name" to JsonPrimitive(":authority"),
                                                                    "exact_match" to JsonPrimitive(route.domain)
                                                                )
                                                            )
                                                        )
                                                    )
                                                    put("route", standardRouteConfig)

                                                    if (!route.isAuthorizationEnabled) {
                                                        put(
                                                            disableAppAuthorization.first,
                                                            disableAppAuthorization.second
                                                        )
                                                    } else {
                                                        // app authorization enabled by not disabling it
                                                    }
                                                }
                                            )
                                        )
                                    }
                                }
                            } + JsonObject(
                                "match" to JsonObject(
                                    "prefix" to JsonPrimitive(""),
                                ),
                                "direct_response" to JsonObject(
                                    "status" to JsonPrimitive(449)
                                ),
                                disableAppAuthorization
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
            useDns: Boolean = false,
        ): EnvoyCluster {
            return EnvoyCluster(
                name,
                type = if (useDns) "LOGICAL_DNS" else "STATIC",
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
