package dk.sdu.cloud.plugins.compute.ucloud

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.*
import java.net.ConnectException
import java.net.URLEncoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

sealed class KubernetesConfigurationSource {
    abstract fun retrieveConnection(): KubernetesConnection?
    abstract fun kubectl(args: List<String>): List<String>

    class KubeConfigFile(val path: String?, val context: String?) : KubernetesConfigurationSource() {
        @Serializable
        private data class KubeConfig(
            val clusters: List<Cluster> = emptyList(),
            val contexts: List<Context> = emptyList(),
            val users: List<User> = emptyList(),
            @SerialName("current-context") val currentContext: String? = null,
        )

        @Serializable
        private data class User(val name: String, val user: UserData) {
            @Serializable
            data class UserData(
                val token: String? = null,
                @SerialName("client-certificate") val clientCertificate: String? = null,
                @SerialName("client-key") val clientKey: String? = null,
                val username: String? = null,
                val password: String? = null,
            )
        }

        @Serializable
        private data class Context(val name: String, val context: ContextData) {
            @Serializable
            data class ContextData(val cluster: String, val user: String)
        }

        @Serializable
        private data class Cluster(val name: String, val cluster: Cluster) {
            @Serializable
            data class Cluster(
                @SerialName("certificate-authority-data")
                val certificateAuthorityData: String? = null,
                @SerialName("certificate-authority")
                val certificateAuthority: String? = null,
                val server: String,
            )
        }

        override fun retrieveConnection(): KubernetesConnection? {
            val kubeConfigFile = File(path ?: (System.getProperty("user.home") + "/.kube/config")) ?: return null

            try {
                val tree = yamlMapper.decodeFromStream(KubeConfig.serializer(), kubeConfigFile.inputStream())
                val actualContextId = context ?: tree.currentContext ?: tree.contexts.singleOrNull()
                ?: error("Unknown context, kube config contains: $tree")

                val actualContext = tree.contexts.find { it.name == actualContextId }
                    ?: error("Unknown context, kube config contains: $tree")

                val cluster = tree.clusters.find { it.name == actualContext.context.cluster }
                    ?: error("Unknown cluster, kube config contains: $tree")

                val user = tree.users.find { it.name == actualContext.context.user }
                    ?: error("Unknown user, kube config contains: $tree")

                val authenticationMethod = when {
                    cluster.cluster.certificateAuthorityData != null -> {
                        log.trace("Using kubectl proxy method")
                        KubernetesAuthenticationMethod.Proxy(context, kubeConfigFile.absolutePath)
                    }

                    user.user.token != null -> {
                        val (username, password) = user.user.token.split(':')
                        KubernetesAuthenticationMethod.BasicAuth(username, password)
                    }

                    user.user.clientCertificate != null && user.user.clientKey != null -> {
                        log.debug(
                            "Client certificate authentication is not currently supported. " +
                                "Falling back to kubectl proxy approach."
                        )

                        KubernetesAuthenticationMethod.Proxy(actualContext.name)
                    }

                    user.user.username != null && user.user.password != null -> {
                        KubernetesAuthenticationMethod.BasicAuth(user.user.username, user.user.password)
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
                if (ex !is FileNotFoundException) {
                    log.warn("Exception caught while parsing kube config at '${kubeConfigFile.absolutePath}'")
                    log.warn(ex.stackTraceToString())
                }
            }

            return null
        }

        override fun kubectl(args: List<String>): List<String> {
            return buildList {
                add("kubectl")
                if (path != null) {
                    add("--kubeconfig")
                    add(path)
                }
                if (context != null) {
                    add("--context")
                    add(context)
                }
                addAll(args)
            }
        }

        companion object : Loggable {
            override val log = logger()

            private val yamlMapper = Yaml(
                configuration = YamlConfiguration(
                    polymorphismStyle = PolymorphismStyle.Property,
                    strictMode = false,
                )
            )
        }
    }

    class InClusterConfiguration(val kubeSvcOverride: String? = null) : KubernetesConfigurationSource() {
        override fun retrieveConnection(): KubernetesConnection? {
            val serviceAccount = File("/var/run/secrets/kubernetes.io/serviceaccount")
                .takeIf { it.exists() } ?: return null

            val token = File(serviceAccount, "token").takeIf { it.exists() }?.readText()
            val caCert = File(serviceAccount, "ca.crt").takeIf { it.exists() }?.inputStream()
            val namespace = File(serviceAccount, "namespace").takeIf { it.exists() }?.readText()

            val certificate = CertificateFactory.getInstance("X.509").generateCertificate(caCert) as X509Certificate
            val alias = "kube-ca"

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry(alias, certificate)
            if (kubeSvcOverride != null) {
                keyStore.setCertificateEntry(
                    kubeSvcOverride.removePrefix("http://").removePrefix("https://").substringBeforeLast(':'),
                    certificate,
                )
            }

            val trustManagerFactory = TrustManagerFactory.getInstance("X509")
            trustManagerFactory.init(keyStore)

            val trustManager = if (trustManagerFactory.trustManagers.isNotEmpty()) {
                trustManagerFactory.trustManagers[0]
            } else {
                null
            }

            if (token == null) return null

            return KubernetesConnection(
                kubeSvcOverride ?: "https://kubernetes.default.svc",
                KubernetesAuthenticationMethod.Token(token),
                namespace ?: "default",
                trustManager
            )
        }

        override fun kubectl(args: List<String>): List<String> {
            return buildList {
                add("kubectl")
                addAll(args)
            }
        }
    }

    object Auto : KubernetesConfigurationSource() {
        private fun findDelegate(): KubernetesConfigurationSource {
            val defaultKubeConfig = KubeConfigFile(null, null)
            if (defaultKubeConfig.retrieveConnection() != null) return defaultKubeConfig

            val inCluster = InClusterConfiguration()
            if (inCluster.retrieveConnection() != null) return inCluster

            val composeKubeConfigFile = File("/mnt/k3s/kubeconfig.yaml")
            if (composeKubeConfigFile.exists()) {
                val newText = composeKubeConfigFile.readText().replace("127.0.0.1", "k3")
                composeKubeConfigFile.writeText(newText)
            }

            return KubeConfigFile("/mnt/k3s/kubeconfig.yaml", null)
        }

        override fun retrieveConnection(): KubernetesConnection? {
            return runCatching { findDelegate() }.getOrNull()?.retrieveConnection()
        }

        override fun kubectl(args: List<String>): List<String> {
            return findDelegate().kubectl(args)
        }
    }
}

fun URLBuilder.fixedClone(): Url {
    val e = HashMap<String, List<String>>()
    parameters.entries().forEach { (k, v) -> e[k] = v }
    return clone().apply {
        parameters.clear()
        for ((k, values) in e) {
            for (v in values) {
                parameters.append(k, v)
            }
        }
    }.build()
}

sealed class KubernetesAuthenticationMethod {
    open fun configureClient(httpClientConfig: HttpClientConfig<*>) {}
    open fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {}
    class Proxy(val context: String?, val configFile: String? = null) : KubernetesAuthenticationMethod() {
        @OptIn(ExperimentalStdlibApi::class)
        private fun startProxy(): Process {
            return ProcessBuilder(
                *buildList {
                    add("kubectl")
                    if (context != null) {
                        add("--context")
                        add(context)
                    }
                    if (configFile != null) {
                        add("--kubeconfig")
                        add(configFile)
                    }
                    add("proxy")
                    add("--disable-filter=true")
                    add("--port")
                    add("42010")
                }.toTypedArray()
            ).start().also { process ->
                Runtime.getRuntime().addShutdownHook(object : Thread() {
                    override fun run() {
                        process.destroyForcibly()
                    }
                })
            }
        }

        private var proxy: Process = startProxy()

        override fun configureRequest(httpRequestBuilder: HttpRequestBuilder) {
            if (!proxy.isAlive) {
                log.warn("Warning kubectl proxy died! Is kubectl configured correctly? ${proxy.pid()}")
                if (!proxy.waitFor(30, TimeUnit.SECONDS)) {
                    proxy.destroyForcibly()
                }
                proxy = startProxy()
            }

            httpRequestBuilder.url(
                URLBuilder(httpRequestBuilder.url.fixedClone()).apply {
                    protocol = when (protocol) {
                        URLProtocol.HTTP, URLProtocol.HTTPS -> URLProtocol.HTTP
                        URLProtocol.WS, URLProtocol.WSS -> URLProtocol.WS
                        else -> URLProtocol.HTTP
                    }

                    host = "localhost"
                    port = 42010
                }.build()
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
    val defaultNamespace: String = "default",
    val trustManager: TrustManager? = null
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
    val namespace: String? = null,
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

    override fun toString(): String {
        return buildString {
            when {
                name == null && namespace == null -> {
                    append("Any resource ")
                }

                name == null && namespace != null -> {
                    append("Any resource in $namespace ")
                }

                name != null && namespace == null -> {
                    append("$name in any namespace ")
                }

                name != null && namespace != null -> {
                    append("$name in $namespace ")
                }
            }

            append("of type $resourceType ($apiGroup/$version)")
        }
    }

    companion object {
        val common = KubernetesResources
    }
}

class KubernetesClient(
    private val configurationSource: KubernetesConfigurationSource = KubernetesConfigurationSource.Auto,
) {
    val conn by lazy {
        configurationSource.retrieveConnection() ?: error("Found no valid Kubernetes configuration")
    }

    fun kubectl(args: List<String>): Process {
        return ProcessBuilder(*configurationSource.kubectl(args).toTypedArray()).start().also { process ->
            Runtime.getRuntime().addShutdownHook(object : Thread() {
                override fun run() {
                    process.destroyForcibly()
                }
            })
        }
    }

    private val httpClient by lazy { ___createClient(longRunning = false) }
    private val backgroundClient by lazy { ___createClient(longRunning = true) }

    private fun selectClient(longRunning: Boolean): HttpClient = if (longRunning) backgroundClient else httpClient

    @Suppress("FunctionName")
    private fun ___createClient(longRunning: Boolean): HttpClient = HttpClient(CIO) {
        conn.authenticationMethod.configureClient(this)
        install(HttpTimeout) {
            this.socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            this.connectTimeoutMillis = if (longRunning) 1000 * 120 else 1000 * 10
            this.requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }

        engine {
            https {
                trustManager = configurationSource.retrieveConnection()?.trustManager
            }
        }

        expectSuccess = false
    }

    fun buildUrl(
        locator: KubernetesResourceLocator,
        queryParameters: Map<String, String>,
        operation: String?,
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

    suspend fun <T> parseResponse(
        serializer: KSerializer<T>,
        resp: HttpResponse,
        errorContext: () -> String,
    ): T {
        if (!resp.status.isSuccess()) {
            throw KubernetesException(
                resp.status,
                "Kubernetes request has failed. Context is: ${errorContext()}\n" +
                    resp.bodyAsChannel().toByteArray(1024 * 4096).decodeToString().prependIndent("    ")
            )
        }

        try {
            // Never read more than 32MB in a response
            // This is just used as a safe-guard against a malicious server
            val data = resp.bodyAsChannel().toByteArray(1024 * 1024 * 32)
            @Suppress("BlockingMethodInNonBlockingContext")
            return defaultMapper.decodeFromString(serializer, data.decodeToString())
        } catch (ex: Exception) {
            throw RuntimeException("Caught an exception while attempting to deserialize message", ex)
        }
    }

    @OptIn(InternalAPI::class)
    suspend fun sendRequest(
        method: HttpMethod,
        locator: KubernetesResourceLocator,
        queryParameters: Map<String, String> = emptyMap(),
        operation: String? = null,
        content: OutgoingContent? = null,
        longRunning: Boolean = false,
    ): HttpStatement {
        var retries = 0
        while (retries < 5) {
            try {
                return selectClient(longRunning).prepareRequest {
                    this.method = method
                    url(buildUrl(locator, queryParameters, operation).also { log.trace("${method.value} $it") })
                    header(HttpHeaders.Accept, ContentType.Application.Json)
                    if (content != null) body = content
                    configureRequest(this)
                }
            } catch (ex: ConnectException) {
                retries++
                delay(1000)
            }
        }
        throw ConnectException("Connection refused after 5 attempts")
    }

    inline fun configureRequest(requestBuilder: HttpRequestBuilder) {
        conn.authenticationMethod.configureRequest(requestBuilder)
    }

    companion object : Loggable {
        override val log = logger()
    }
}

suspend fun <T> KubernetesClient.getResource(
    serializer: KSerializer<T>,
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): T {
    return parseResponse(
        serializer,
        sendRequest(HttpMethod.Get, locator, queryParameters, operation).execute(),
        { "getResource($locator, $queryParameters, $operation)" }
    )
}

data class KubernetesList<T>(
    val items: List<T>,
    val continueAt: String? = null,
) : List<T> by items

suspend fun <T> KubernetesClient.listResources(
    serializer: KSerializer<T>,
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): KubernetesList<T> {
    val resp = sendRequest(HttpMethod.Get, locator, queryParameters, operation)

    val parsedResp = parseResponse(
        JsonObject.serializer(),
        resp.execute(),
        { "listResources($locator, $queryParameters, $operation)" }
    )
    val items = parsedResp["items"] as? JsonArray ?: error("Could not parse items of response: $parsedResp")

    return KubernetesList(
        (0 until items.size).map {
            defaultMapper.decodeFromJsonElement(serializer, items[it])
        },

        ((parsedResp["metadata"] as? JsonObject)?.get("continue") as? JsonPrimitive)?.contentOrNull
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T : WatchEvent<*>> KubernetesClient.watchResource(
    serializer: KSerializer<T>,
    scope: CoroutineScope,
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): ReceiveChannel<T> {
    return scope.produce {
        val resp = sendRequest(
            HttpMethod.Get,
            locator,
            queryParameters + mapOf("watch" to "true"),
            operation
        )
        val content = resp.execute().bodyAsChannel()
        while (isActive) {
            val nextLine = content.readUTF8Line() ?: break
            @Suppress("BlockingMethodInNonBlockingContext")
            send(defaultMapper.decodeFromString(serializer, nextLine))
        }

        runCatching { content.cancel() }
    }
}

suspend inline fun KubernetesClient.deleteResource(
    locator: KubernetesResourceLocator,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonObject {
    return parseResponse(
        JsonObject.serializer(),
        sendRequest(HttpMethod.Delete, locator, queryParameters, operation).execute(),
        { "deleteResource($locator, $queryParameters, $operation)" }
    )
}

suspend inline fun KubernetesClient.replaceResource(
    locator: KubernetesResourceLocator,
    replacementJson: String,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonObject {
    return parseResponse(
        JsonObject.serializer(),
        sendRequest(
            HttpMethod.Put,
            locator,
            queryParameters,
            operation,
            TextContent(replacementJson, ContentType.Application.Json)
        ).execute(),
        { "replaceResource($locator, $replacementJson, $queryParameters, $operation)" }
    )
}

suspend fun KubernetesClient.patchResource(
    locator: KubernetesResourceLocator,
    replacement: String,
    contentType: ContentType = ContentType("application", "merge-patch+json"),
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonObject {
    return parseResponse(
        JsonObject.serializer(),
        sendRequest(
            HttpMethod.Patch,
            locator,
            queryParameters,
            operation,
            TextContent(replacement, contentType)
        ).execute(),
        { "patchResource($locator, $replacement, $contentType, $queryParameters, $operation)" }
    )
}

suspend fun KubernetesClient.createResource(
    locator: KubernetesResourceLocator,
    replacement: String,
    contentType: ContentType = ContentType.Application.Json,
    queryParameters: Map<String, String> = emptyMap(),
    operation: String? = null,
): JsonObject {
    return parseResponse(
        JsonObject.serializer(),
        sendRequest(
            HttpMethod.Post,
            locator,
            queryParameters,
            operation,
            TextContent(replacement, contentType)
        ).execute(),
        { "createResource($locator, $replacement, $contentType, $queryParameters, $operation)" }
    )
}

const val API_GROUP_CORE = ""
const val API_GROUP_LEGACY = API_GROUP_CORE

// NOTE(Dan): Namespaces must be valid DNS names. Prepending with '#' guarantees that this won't be valid.
const val NAMESPACE_ANY = "#ANY"

class KubernetesException(
    val statusCode: HttpStatusCode,
    val responseBody: String,
) : RuntimeException("Kubernetes returned an error: $statusCode. Reason: $responseBody")
