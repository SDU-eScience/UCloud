package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dk.sdu.cloud.service.Loggable
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import okhttp3.ConnectionPool
import java.io.File
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
                        KubernetesAuthenticationMethod.Token(user.user.token)
                    }

                    user.user.clientCertificate != null && user.user.clientKey != null -> {
                        val cert = File(user.user.clientCertificate).takeIf { it.exists() }?.readText()
                        val key = File(user.user.clientKey).takeIf { it.exists() }?.readText()
                        if (cert == null || key == null) error("Could not load cert/key at ${user.user}")

                        KubernetesAuthenticationMethod.Certificate(cert, key)
                    }

                    else -> {
                        error("Found no supported authentication method for $user")
                    }
                }

                return KubernetesConnection(
                    cluster.cluster.server,
                    authenticationMethod,
                    cluster.cluster.certificateAuthorityData?.let {
                        Base64.getDecoder().decode(it).toString(Charsets.UTF_8)
                    } ?: cluster.cluster.certificateAuthority?.let { file ->
                        File(file).takeIf { it.exists() }?.readText()
                    }
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
                caCert,
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

sealed class KubernetesAuthenticationMethod {
    open fun configureClient(httpClientConfig: HttpClientConfig<OkHttpConfig>) {}
    open fun configureRequest() {}
    class Certificate(val cert: String, val key: String) : KubernetesAuthenticationMethod() {
        override fun toString(): String {
            return "Certificate(cert='$cert')"
        }
    }

    class Token(val token: String) : KubernetesAuthenticationMethod() {
        override fun toString(): String {
            return "Token()"
        }
    }

    class BasicAuth(val username: String, val password: String) : KubernetesAuthenticationMethod() {
        override fun toString(): String {
            return "BasicAuth(username='$username')"
        }
    }
}

class KubernetesConnection(
    masterUrl: String,
    val authenticationMethod: KubernetesAuthenticationMethod,
    val certificateAuthority: String? = null,
    val defaultNamespace: String = "default"
) {
    val masterUrl = masterUrl.removeSuffix("/")
    override fun toString(): String {
        return "KubernetesConnection(" +
            "authenticationMethod=$authenticationMethod, " +
            "certificateAuthority=$certificateAuthority, " +
            "defaultNamespace='$defaultNamespace', " +
            "masterUrl='$masterUrl'" +
            ")"
    }
}

class KubernetesClient(
    private val configurationSource: KubernetesConfigurationSource = KubernetesConfigurationSource.Auto
) {
    private val conn = configurationSource.retrieveConnection() ?: error("Found no valid Kubernetes configuration")
    private val httpClient = HttpClient(OkHttp) {
        conn.authenticationMethod.configureClient(this)

        engine {
            config {
                readTimeout(5, TimeUnit.MINUTES)
                writeTimeout(5, TimeUnit.MINUTES)

                connectionPool(ConnectionPool(8, 30, TimeUnit.MINUTES))
            }
        }
    }

    init {
        log.info("Kubernetes client will use the following connection parameters: $conn")
    }

    suspend fun getResource() {}

    suspend fun postResource() {}

    suspend fun patchResource() {}

    suspend fun deleteResource() {}

    suspend fun watchResource() {}

    companion object : Loggable {
        override val log = logger()
    }
}
