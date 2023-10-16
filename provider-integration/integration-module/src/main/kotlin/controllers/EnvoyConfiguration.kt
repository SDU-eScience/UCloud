package dk.sdu.cloud.controllers

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.UCLOUD_IM_PORT
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.calls.client.urlEncode
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.*

class EnvoyConfigurationService(
    private val executable: String?,
    private val funceWrapper: Boolean,
    private val configDir: String,
    private val useUCloudUsernameHeader: Boolean,
    private val logDirectory: String,
    private val providerId: String? = null,
    private val downstreamTls: Boolean,
    private val internalAddressToProvider: String,
    private val envoyIsManagedExternally: Boolean,
) {
    constructor(config: VerifiedConfig) : this(
        config.server.envoy.executable,
        config.server.envoy.funceWrapper,
        config.server.envoy.directory,
        config.core.launchRealUserInstances,
        config.core.logs.directory,
        config.core.providerId,
        config.server.envoy.downstreamTls,
        config.server.envoy.internalAddressToProvider,
        config.server.envoy.envoyIsManagedExternally,
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

        object Nop : ConfigurationMessage()
    }

    private val channel = Channel<ConfigurationMessage>(Channel.BUFFERED)
    private val autoConfigureEnabled = AtomicBoolean(false)

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
        listenAddress: String?,
        port: Int?,
        initClusters: Collection<EnvoyCluster>? = null,
        initRoutes: Collection<EnvoyRoute>? = null,
    ) {
        require((initClusters == null && initRoutes == null) || (initClusters != null && initRoutes != null))

        installErrorPages()
        writeConfigurationFile(listenAddress ?: "0.0.0.0", port ?: 8889)

        val logFile = "/${logDirectory}/envoy-$providerId.log"

        val envoyProcess = if (!envoyIsManagedExternally) {
            startProcess(
                args = buildList {
                    add(executable ?: "/usr/local/bin/getenvoy")
                    if (funceWrapper) add("run")
                    add("--config-path")
                    add("$configDir/$configFile")
                },
                envs = listOf("ENVOY_VERSION=1.23.0"),
                attachStdout = true,
                attachStderr = true,
                attachStdin = false,
                logFile = File(logFile),
            )
        } else {
            // Nothing to do
            null
        }

        val routes = hashSetOf<EnvoyRoute>()
        val clusters = hashMapOf<String, EnvoyCluster>()

        val job = ProcessingScope.launch {
            if (initClusters != null && initRoutes != null) {
                routes.addAll(initRoutes)
                for (cluster in initClusters) clusters[cluster.name] = cluster
            } else {
                routes.add(EnvoyRoute.Standard(null, IM_SERVER_CLUSTER))
                clusters[IM_SERVER_CLUSTER] = EnvoyCluster.create(
                    IM_SERVER_CLUSTER,
                    internalAddressToProvider,
                    UCLOUD_IM_PORT,
                    useDns = !internalAddressToProvider[0].isDigit()
                )
                if (providerId != null) routes.add(EnvoyRoute.WebAuthorizeSession(providerId, IM_SERVER_CLUSTER))
            }

            // NOTE(Dan): A call to enableAutoConfigure() will guarantee that the IM is configured

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

                    ConfigurationMessage.Nop -> {
                        // Do nothing
                    }
                }

                configure(routes, clusters.values)
            }
        }

        if (envoyProcess != null) {
            ProcessWatcher.addWatchBlocking(envoyProcess) { statusCode ->
                log.warn("Envoy has died unexpectedly! It exited with $statusCode.")
                log.warn("You might be able to read additional information from: $logFile")
                log.warn("We will attempt to restart Envoy now!")
                job.cancel()
                while (job.isActive) delay(50)
                start(listenAddress, port, clusters.values, routes)
            }
        }
    }

    private fun installErrorPages() {
        val tempFile = Files.createTempFile("", ".html").toFile()
        tempFile.writeText(badGatewayHtml)
        Files.move(tempFile.toPath(), File(configDir, badGatewayFile).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    fun disableAutoConfigure() {
        autoConfigureEnabled.set(false)
    }

    suspend fun enableAutoConfigure() {
        autoConfigureEnabled.set(true)
        channel.send(ConfigurationMessage.Nop)
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
        if (!autoConfigureEnabled.get()) return

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

    private fun writeConfigurationFile(listenAddress: String, port: Int) {
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
          address: $listenAddress
          port_value: $port
      filter_chains:
        - filters:
            - name: envoy.filters.network.http_connection_manager
              typed_config:
                "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
                codec_type: HTTP1
                local_reply_config:
                  mappers:
                    - filter:
                        status_code_filter:
                          comparison:
                            op: EQ
                            value:
                              default_value: 503
                              runtime_key: key_bad_gateway
                      body:
                        filename: $configDir/$badGatewayFile
                      body_format_override:
                        text_format: "%LOCAL_REPLY_BODY%"
                        content_type: "text/html; charset=UTF-8"

                http_filters:
                - name: envoy.filters.http.router
                  typed_config:
                    "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
                stat_prefix: ingress_http
                rds:
                  route_config_name: local_route
                  config_source:
                    path: $configDir/$rdsFile

$tlsDefaultDownstream

          """
        )
    }

    companion object : Loggable {
        override val log = logger()
        private const val tempPrefix = "temp-"
        private const val rdsFile = "rds.yaml"
        private const val clustersFile = "clusters.yaml"
        private const val configFile = "config.yaml"
        private const val badGatewayFile = "bad-gateway.html"

        const val IM_SERVER_CLUSTER = "_UCloud"

        private const val badGatewayHtml = """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport"
                      content="width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0">
                <meta http-equiv="X-UA-Compatible" content="ie=edge">
                <title>Job is unavailable | UCloud</title>
                <style>
                    body {
                        font-family: 'IBM Plex Sans', sans-serif;
                        background: #282c35;
                        color: white;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        flex-direction: column;
                        height: 100vh;
                        width: 100vw;
                        margin: 0;
                        padding: 0;
                    }

                    section {
                        width: 800px;
                        height: auto;
                        display: flex;
                        flex-direction: column;
                    }

                    img {
                        height: 128px;
                        align-self: center;
                    }

                    h1 {
                        align-self: center;
                        margin: 24px 0;
                    }

                    p {
                        margin: 0;
                    }

                    ul {
                        margin: 24px 0;
                    }
                </style>
            </head>
            <body>

            <section>
            <img src="data:image/png;base64,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" alt="Logo" />

            <h1>Your job is currently unavailable</h1>

            <p>This is normally caused by:</p>

            <ul>
                <li>Your job is starting but not yet ready to receive requests</li>
                <li>Your job has completed and is no longer available</li>
                <li>Your active session has expired</li>
            </ul>

            <p>To resolve this, try one of the following:</p>

            <ul>
                <li>Refresh this page later (this page will automatically refresh in <span id="refresh-in">15</span> seconds)</li>
                <li>Open the job on UCloud and check the output for errors</li>
                <li>Click 'Open interface' from the UCloud platform (this will create a new session for you)</li>
                <li>If the above does not work, try restarting your job</li>
                <li>If restarting does not work, try contacting support from the UCloud platform</li>
            </ul>
            </section>

            <script>
                window.onload = () => {
                    const refreshIn = document.querySelector("#refresh-in");
                    let timeRemaining = 15;
                    setInterval(() => {
                        timeRemaining--;
                        if (timeRemaining < 0) timeRemaining = 0;
                        refreshIn.innerText = timeRemaining.toString();

                        if (timeRemaining === 0) {
                            window.location.reload();
                        }
                    }, 1000);
                };
            </script>
            </body>
            </html>
        """
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
        val tokensRequired: List<String>?,
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
                                                )
                                            )

                                            if (useUCloudUsernameHeader && ucloudIdentity != null) {
                                                add(
                                                    JsonObject(
                                                        "match" to JsonObject(
                                                            buildMap {
                                                                put("prefix", JsonPrimitive("/"))
                                                                put(
                                                                    "query_parameters", JsonArray(
                                                                        JsonObject(
                                                                            mapOf(
                                                                                "name" to JsonPrimitive("usernameHint"),
                                                                                "string_match" to JsonObject(
                                                                                    mapOf(
                                                                                        "exact" to JsonPrimitive(
                                                                                            base64Encode(ucloudIdentity.encodeToByteArray())
                                                                                        )
                                                                                    )
                                                                                )
                                                                            )
                                                                        )
                                                                    )
                                                                )
                                                            }
                                                        ),
                                                        "route" to standardRouteConfig,
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
                                                                buildList {
                                                                    add(JsonObject(
                                                                        "name" to JsonPrimitive(":authority"),
                                                                        "exact_match" to JsonPrimitive(route.domain)
                                                                    ))

                                                                    if (route.tokensRequired != null) {
                                                                        val regex = buildString {
                                                                            append(".*ucloud-compute-session-.*=(")
                                                                            var isFirst = true
                                                                            for (tok in route.tokensRequired) {
                                                                                if (!isFirst) {
                                                                                    append("|")
                                                                                }

                                                                                append(urlEncode(tok))

                                                                                isFirst = false
                                                                            }
                                                                            append(").*")
                                                                        }

                                                                        add(
                                                                            JsonObject(
                                                                                "name" to JsonPrimitive("cookie"),
                                                                                "string_match" to JsonObject(
                                                                                    "safe_regex" to JsonObject(
                                                                                        "regex" to JsonPrimitive(regex)
                                                                                    )
                                                                                )
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                            )
                                                        )
                                                    )
                                                    put("route", standardRouteConfig)
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
                type = if (useDns) "STRICT_DNS" else "STATIC",
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
