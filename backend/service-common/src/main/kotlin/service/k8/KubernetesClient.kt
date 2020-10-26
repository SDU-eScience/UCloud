package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.isActive
import java.io.File
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.TimeUnit

sealed class KubernetesConfigurationSource {
    abstract fun retrieveConnection(): KubernetesConnection?

    class KubeConfigFile(val path: String?, val context: String?) : KubernetesConfigurationSource() {
        private data class KubeConfig(
            val clusters: List<Cluster>,
            val contexts: List<Context>,
            val users: List<User>,
            @JsonAlias("current-context") val currentContext: String?
        )

        private data class User(val name: String, val user: UserData) {
            data class UserData(
                val token: String?,
                @JsonAlias("client-certificate") val clientCertificate: String?,
                @JsonAlias("client-key") val clientKey: String?
            )
        }

        private data class Context(val name: String, val context: ContextData) {
            data class ContextData(val cluster: String, val user: String)
        }

        private data class Cluster(val name: String, val cluster: Cluster) {
            data class Cluster(
                @JsonAlias("certificate-authority-data") val certificateAuthorityData: String?,
                @JsonAlias("certificate-authority") val certificateAuthority: String?,
                val server: String
            )
        }

        override fun retrieveConnection(): KubernetesConnection? {
            val kubeConfigFile = File(path ?: System.getProperty("user.home") + "/.kube/config") ?: return null

            try {
                val tree = yamlMapper.readValue<KubeConfig>(kubeConfigFile)
                val actualContextId = context ?: tree.currentContext ?: tree.contexts.singleOrNull()
                ?: error("Unknown context, kube config contains: $tree")

                val actualContext = tree.contexts.find { it.name == actualContextId }
                    ?: error("Unknown context, kube config contains: $tree")

                val cluster = tree.clusters.find { it.name == actualContext.context.cluster }
                    ?: error("Unknown cluster, kube config contains: $tree")

                val user = tree.users.find { it.name == actualContext.context.user }
                    ?: error("Unknown user, kube config contains: $tree")

                val authenticationMethod = when {
                    user.user.token != null -> {
                        val (username, password) = user.user.token.split(':')
                        KubernetesAuthenticationMethod.BasicAuth(username, password)
                    }

                    user.user.clientCertificate != null && user.user.clientKey != null -> {
                        log.info(
                            "Client certificate authentication is not currently supported. " +
                                "Falling back to kubectl proxy approach."
                        )

                        KubernetesAuthenticationMethod.Proxy(actualContext.name)
                    }

                    else -> {
                        error("Found no supported authentication method for $user")
                    }
                }

                return KubernetesConnection(
                    cluster.cluster.server,
                    authenticationMethod,
                )
            } catch (ex: Throwable) {
                log.warn("Exception caught while parsing kube config at '${kubeConfigFile.absolutePath}'")
                log.warn(ex.stackTraceToString())
            }

            return null
        }

        companion object : Loggable {
            override val log = logger()

            private val yamlFactory = YAMLFactory()
            private val yamlMapper = ObjectMapper(yamlFactory).apply {
                configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                registerKotlinModule()
            }
        }
    }

    object InClusterConfiguration : KubernetesConfigurationSource() {
        override fun retrieveConnection(): KubernetesConnection? {
            val serviceAccount = File("/var/run/secrets/kubernetes.io/serviceaccount")
                .takeIf { it.exists() } ?: return null

            val token = File(serviceAccount, "token").takeIf { it.exists() }?.readText()
            val caCert = File(serviceAccount, "ca.crt").takeIf { it.exists() }?.readText()
            val namespace = File(serviceAccount, "namespace").takeIf { it.exists() }?.readText()

            if (token == null) return null

            return KubernetesConnection(
                "https://kubernetes.default.svc",
                KubernetesAuthenticationMethod.Token(token),
                namespace ?: "default"
            )
        }
    }

    object Auto : KubernetesConfigurationSource() {
        override fun retrieveConnection(): KubernetesConnection? {
            val inCluster = InClusterConfiguration.retrieveConnection()
            if (inCluster != null) return inCluster

            val defaultKubeConfig = KubeConfigFile(null, null).retrieveConnection()
            if (defaultKubeConfig != null) return defaultKubeConfig

            return null
        }
    }
}

fun URLBuilder.fixedClone(): Url {
    val e = HashMap<String, List<String>>()
    parameters.entries().forEach { (k, v) -> e[k] = v }
    parameters.clear()
    return clone().build().copy(
        parameters = Parameters.build {
            for ((k, values) in e) {
                for (v in values) {
                    append(k, v)
                }
            }
        }
    )
}

sealed class KubernetesAuthenticationMethod {
    open fun configureClient(httpClientConfig: HttpClientConfig<*>) {}
    open fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {}
    class Proxy(val context: String) : KubernetesAuthenticationMethod() {
        private fun startProxy(): Process {
            return ProcessBuilder(
                "kubectl",
                "--context",
                context,
                "proxy",
                "--disable-filter=true", // allow exec into pods, TODO Put this behind a flag to opt-in
                "--port",
                "42010"
            ).start()
        }

        private var proxy: Process = startProxy()

        override fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {
            if (!proxy.isAlive) {
                log.warn("Warning kubectl proxy died! Is kubectl configured correctly?")
                if (!proxy.waitFor(30, TimeUnit.SECONDS)) {
                    proxy.destroyForcibly()
                }
                proxy = startProxy()
            }

            httpRequestBuilder.url(
                httpRequestBuilder.url.fixedClone().copy(
                    protocol = URLProtocol.HTTP,
                    host = "localhost",
                    specifiedPort = 42010,
                ).toString()
            )
        }

        companion object : Loggable {
            override val log = logger()
        }
    }

    class Token(val token: String) : KubernetesAuthenticationMethod() {
        override fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {
            with(httpRequestBuilder) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }

        override fun toString(): String {
            return "Token()"
        }
    }

    class BasicAuth(val username: String, val password: String) : KubernetesAuthenticationMethod() {
        private val header = "Basic " + Base64.getEncoder()
            .encode("$username:$password".toByteArray())
            .toString(Charsets.UTF_8)

        override fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {
            with(httpRequestBuilder) {
                header(HttpHeaders.Authorization, header)
            }
        }

        override fun toString(): String {
            return "BasicAuth(username='$username')"
        }
    }
}

class KubernetesConnection(
    masterUrl: String,
    val authenticationMethod: KubernetesAuthenticationMethod,
    val defaultNamespace: String = "default"
) {
    val masterUrl = masterUrl.removeSuffix("/")
    override fun toString(): String {
        return "KubernetesConnection(" +
            "authenticationMethod=$authenticationMethod, " +
            "defaultNamespace='$defaultNamespace', " +
            "masterUrl='$masterUrl'" +
            ")"
    }
}

data class KubernetesResourceLocator(
    val apiGroup: String,
    val version: String,
    val resourceType: String,
    val name: String? = null,
    val namespace: String? = null
) {
    fun withName(name: String): KubernetesResourceLocator = copy(name = name)

    /**
     * Locates Kubernetes resources in the namespace identified by [namespace]
     *
     * If [namespace] == [NAMESPACE_ANY] then all namespaces will be considered.
     */
    fun withNamespace(namespace: String): KubernetesResourceLocator = copy(namespace = namespace)
    fun withNameAndNamespace(name: String, namespace: String): KubernetesResourceLocator =
        copy(name = name, namespace = namespace)

    companion object {
        val common = KubernetesResources
    }
}

class KubernetesClient(
    private val configurationSource: KubernetesConfigurationSource = KubernetesConfigurationSource.Auto
) {
    @PublishedApi
    internal val conn = configurationSource.retrieveConnection() ?: error("Found no valid Kubernetes configuration")

    @PublishedApi
    internal val httpClient = HttpClient(CIO) {
        // NOTE(Dan): Not using OkHttp here because of a input buffering issue causing watched resources to not
        // always flush. It is not clear to me how to make OkHttp/Ktor/??? not do this input buffering. Using the CIO
        // engine seemingly resolves the issue.
        conn.authenticationMethod.configureClient(this)
        install(HttpTimeout) {
            this.socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            this.connectTimeoutMillis = 1000 * 60
            this.requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
    }

    init {
        log.info("Kubernetes client will use the following connection parameters: $conn")
    }

    fun buildUrl(
        locator: KubernetesResourceLocator,
        queryParameters: Map<String, String>,
        operation: String?
    ): String = buildString {
        with(locator) {
            append(conn.masterUrl)
            append('/')
            if (apiGroup == API_GROUP_LEGACY) {
                append("api/")
            } else {
                append("apis/")
                append(apiGroup)
                append('/')
            }
            append(version)
            append('/')
            if (namespace != NAMESPACE_ANY) {
                append("namespaces/")
                append(namespace ?: conn.defaultNamespace)
                append('/')
            }
            append(resourceType)
            if (name != null) {
                append('/')
                append(name)
            }
            if (operation != null) {
                append('/')
                append(operation)
            }
            append(encodeQueryParamsToString(queryParameters))
        }
    }

    private fun String.urlEncode() = URLEncoder.encode(this, "UTF-8")
    private fun encodeQueryParamsToString(queryPathMap: Map<String, String>): String {
        return queryPathMap
            .map {
                if (it.value.isEmpty()) {
                    it.key.urlEncode()
                } else {
                    it.key.urlEncode() + "=" + it.value.urlEncode()
                }
            }
            .joinToString("&")
            .takeIf { it.isNotEmpty() }
            ?.let { "?$it" } ?: ""
    }

    suspend inline fun <reified T> parseResponse(resp: HttpResponse): T {
        if (!resp.status.isSuccess()) {
            throw KubernetesException(
                resp.status,
                resp.content.toByteArray(1024 * 4096).decodeToString()
            )
        }

        try {
            // Never read more than 32MB in a response
            // This is just used as a safe-guard against a malicious server
            val data = resp.content.toByteArray(1024 * 1024 * 32)
            @Suppress("BlockingMethodInNonBlockingContext")
            return defaultMapper.readValue(data)
        } catch (ex: Exception) {
            throw RuntimeException("Caught an exception while attempting to deserialize message", ex)
        }
    }

    inline suspend fun <reified T> sendRequest(
        method: HttpMethod,
        locator: KubernetesResourceLocator,
        queryParameters: Map<String, String> = emptyMap(),
        operation: String? = null,
        content: OutgoingContent? = null,
    ): T {
        return httpClient.request {
            this.method = method
            url(buildUrl(locator, queryParameters, operation).also { log.debug("${method.value} $it") })
            header(HttpHeaders.Accept, ContentType.Application.Json)
            if (content != null) body = content
            configureRequest(this)
        }
    }

    inline fun configureRequest(requestBuilder: HttpRequestBuilder) {
        conn.authenticationMethod.configureRequest(requestBuilder)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend inline fun <reified T> KubernetesClient.getResource(
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): T {
    return parseResponse(sendRequest(HttpMethod.Get, locator, queryParameters, operation))
}

data class KubernetesList<T>(
    val items: List<T>,
    val continueAt: String? = null
) : List<T> by items

suspend inline fun <reified T> KubernetesClient.listResources(
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): KubernetesList<T> {
    val resp = sendRequest<HttpResponse>(HttpMethod.Get, locator, queryParameters, operation)

    val parsedResp = parseResponse<JsonNode>(resp)
    val items = parsedResp["items"].takeIf { it.nodeType == JsonNodeType.ARRAY }
        ?: error("Could not parse items of response: $parsedResp")

    return KubernetesList(
        (0 until items.size()).map {
            defaultMapper.treeToValue(items[it])
        },
        parsedResp.get("metadata")?.get("continue")?.asText()
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
inline fun <reified T : WatchEvent<*>> KubernetesClient.watchResource(
    scope: CoroutineScope,
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): ReceiveChannel<T> {
    return scope.produce {
        val resp = sendRequest<HttpStatement>(
            HttpMethod.Get,
            locator,
            queryParameters + mapOf("watch" to "true"),
            operation
        )
        val content = resp.receive<ByteReadChannel>()
        while (isActive) {
            val nextLine = content.readUTF8Line() ?: break
            @Suppress("BlockingMethodInNonBlockingContext")
            send(defaultMapper.readValue<T>(nextLine))
        }

        runCatching { content.cancel() }
    }
}

suspend inline fun KubernetesClient.deleteResource(
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonNode {
    return parseResponse(sendRequest(HttpMethod.Delete, locator, queryParameters, operation))
}

suspend inline fun KubernetesClient.replaceResource(
    locator: KubernetesResourceLocator,
    replacementJson: String,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonNode {
    return parseResponse(
        sendRequest(
            HttpMethod.Put,
            locator,
            queryParameters,
            operation,
            TextContent(replacementJson, ContentType.Application.Json)
        )
    )
}

suspend fun KubernetesClient.patchResource(
    locator: KubernetesResourceLocator,
    replacement: String,
    contentType: ContentType = ContentType("application", "merge-patch+json"),
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonNode {
    return parseResponse(
        sendRequest(
            HttpMethod.Patch,
            locator,
            queryParameters,
            operation,
            TextContent(replacement, contentType)
        )
    )
}

suspend fun KubernetesClient.createResource(
    locator: KubernetesResourceLocator,
    replacement: String,
    contentType: ContentType = ContentType.Application.Json,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonNode {
    return parseResponse(
        sendRequest(
            HttpMethod.Post,
            locator,
            queryParameters,
            operation,
            TextContent(replacement, contentType)
        )
    )
}

const val API_GROUP_CORE = ""
const val API_GROUP_LEGACY = API_GROUP_CORE

// NOTE(Dan): Namespaces must be valid DNS names. Prepending with '#' guarantees that this won't be valid.
const val NAMESPACE_ANY = "#ANY"

class KubernetesException(
    val statusCode: HttpStatusCode,
    val responseBody: String
) : RuntimeException("Kubernetes returned an error: $statusCode. Reason: $responseBody")
