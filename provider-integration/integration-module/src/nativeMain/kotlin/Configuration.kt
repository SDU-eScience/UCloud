package dk.sdu.cloud

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.normalizeCertificate
import dk.sdu.cloud.utils.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class ProductReferenceWithoutProvider(
    @UCloudApiDoc("The `Product` ID")
    val id: String,

    @UCloudApiDoc("The ID of the `Product`'s category")
    val category: String,
)

@Serializable
data class PartialProductReferenceWithoutProvider(
    @UCloudApiDoc("The `Product` ID or null. If null this targets all products in the category.")
    val id: String? = null,
    @UCloudApiDoc("The ID of the `Product`'s category or null. If null this targets all categories.")
    val category: String? = null,
) {
    init {
        require(id == null || category != null) { "id cannot be specified without category" }
    }

    fun matches(ref: ProductReferenceWithoutProvider): Boolean {
        if (category == null) return true
        if (category == ref.category && id == null) return true
        if (category == ref.category && id == ref.id) return true
        return false
    }
}


@Serializable
data class ProductBasedConfiguration(
    val products: List<ProductReferenceWithoutProvider>,
    val plugins: List<PluginConfiguration>
) {
    @Serializable
    data class PluginConfiguration(
        val id: String,
        val activeFor: List<PartialProductReferenceWithoutProvider> = listOf(PartialProductReferenceWithoutProvider()),
        val name: String? = null,
        val configuration: JsonObject? = null,
    )
}

inline fun <reified Config> ProductBasedConfiguration.completeConfiguration(): Map<Config, List<ProductReferenceWithoutProvider>> {
    return plugins.mapNotNull { plugin ->
        val cfg = plugin.configuration ?: return@mapNotNull null
        val activeFor = products.filter { product ->
            plugin.activeFor.any { it.matches(product) }
        }

        try {
            defaultMapper.decodeFromJsonElement<Config>(cfg) to activeFor
        } catch (ex: Throwable) {
            throw IllegalStateException("Invalid configuration found", ex)
        }
    }.toMap()
}

inline fun <reified Config> ProductBasedConfiguration.config(product: ProductReference): Config {
    val ref = ProductReferenceWithoutProvider(product.id, product.category)
    val relevantConfig = plugins.find { config -> config.activeFor.any { it.matches(ref) } }
        ?.configuration ?: error("No configuration found for product: $ref")

    return try {
        defaultMapper.decodeFromJsonElement(relevantConfig)
    } catch (ex: Throwable) {
        throw IllegalStateException("Invalid configuration found for $ref", ex)
    }
}

sealed class ConfigurationException(message: String) : RuntimeException(message) {
    class IsBeingInstalled(
        val core: IMConfiguration.Core,
        val server: IMConfiguration.Server,
    ) : ConfigurationException("UCloud/IM is currently being installed")

    class BadConfiguration(message: String) : ConfigurationException(message)
}

class IMConfiguration(
    val configLocation: String,
    val serverMode: ServerMode,
    val core: Core,
    val plugins: Plugins,
    val server: Server?,
    val frontendProxy: FrontendProxy?,
) {
    companion object {
        const val PLACEHOLDER_ID = "PLACEHOLDER"
        const val CONFIG_PATH = "/etc/ucloud"

        fun load(serverMode: ServerMode): IMConfiguration {
            val core = Json.decodeFromString<Core>(
                NativeFile.open("$CONFIG_PATH/core.json", readOnly = true).readText()
            ).normalize()

            val server = runCatching {
                Json.decodeFromString<Server>(
                    NativeFile.open("$CONFIG_PATH/server.json", readOnly = true).readText()
                ).normalize(CONFIG_PATH)
            }.getOrNull()

            if (server == null && serverMode == ServerMode.Server) {
                throw IllegalStateException("Could not read server section")
            }

            if (core.providerId == PLACEHOLDER_ID) {
                throw ConfigurationException.IsBeingInstalled(
                    core,
                    server ?: throw ConfigurationException.BadConfiguration(
                        "UCloud/IM is not ready to be launched outside of server mode"
                    )
                )
            }

            val plugins = Json.decodeFromString<Plugins>(
                NativeFile.open("$CONFIG_PATH/plugins.json", readOnly = true).readText()
            )

            // NOTE(Dan): Deployment notes. The configuration should be on both the frontend and the dedicated node.
            // The frontend proxy needs to be in the same group as the ucloud user, but run as a separate user.
            // The ucloud user should be able to read all the configuration files. The frontend proxy should not be
            // able to read the server configuration.
            val frontendProxy = runCatching {
                Json.decodeFromString<FrontendProxy>(
                    NativeFile.open("$CONFIG_PATH/frontend_proxy.json", readOnly = true).readText()
                )
            }.getOrNull()

            return IMConfiguration(CONFIG_PATH, serverMode, core, plugins, server, frontendProxy)
        }
    }

    @Serializable
    data class DevelopmentInstance(
        val username: String,
        val userId: Int,
        val port: Int,
    )

    @Serializable
    data class Core(
        val providerId: String,
        val certificateFile: String? = null,
        val certificate: String? = null,
        val ipcDirectory: String? = null,
        // NOTE(Dan): If this is specified, this instance will never be launched
        val developmentInstance: DevelopmentInstance? = null,
        // NOTE(Dan): Location where this integration module is publicly accessible, should match the configuration
        // provided to UCloud/Core. Default value is `null` which will cause some plugins to not work.
        val ownHost: Host? = null,
    ) {
        fun normalize(): Core {
            if (providerId != PLACEHOLDER_ID) {
                val certificate = if (certificateFile != null) {
                    NativeFile.open(certificateFile, readOnly = true).readText()
                } else {
                    certificate
                } ?: throw IllegalStateException("No certificate available")

                return copy(
                    certificateFile = null,
                    certificate = normalizeCertificate(certificate),
                )
            }

            return this
        }
    }

    @Serializable
    data class Plugins(
        val files: ProductBasedConfiguration? = null,
        val fileCollection: ProductBasedConfiguration? = null,
        val compute: ProductBasedConfiguration? = null,
        val connection: JsonObject? = null,
        val projects: JsonObject? = null,
    )

    @Serializable
    data class Server(
        val refreshToken: String,
        val ucloud: Host,
        val dbFile: String = "",
        val port: Int? = null,
    ) {
        fun normalize(configLocation: String): Server {
            val newDbFile = if (dbFile == "") {
                "$configLocation/db.sqlite3"
            } else {
                dbFile
            }

            return copy(dbFile = newDbFile)
        }
    }

    @Serializable
    data class Host(val host: String, val scheme: String, val port: Int) {
        override fun toString() = buildString {
            append(scheme)
            append("://")
            append(host)
            append(":")
            append(port)
        }
    }

    @Serializable
    data class FrontendProxy(
        val remoteHost: String,
        val remotePort: Int,
        val remoteScheme: String,
        val sharedSecret: String,
    )
}
