package dk.sdu.cloud

import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.fileIsDirectory
import dk.sdu.cloud.utils.homeDirectory
import dk.sdu.cloud.utils.readText
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import platform.posix.getenv

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
    class PluginConfiguration(
        val id: String,
        val activeFor: List<PartialProductReferenceWithoutProvider> = listOf(PartialProductReferenceWithoutProvider()),
        val name: String? = null,
        val configuration: JsonObject? = null,
    )
}

class IMConfiguration(
    val configLocation: String,
    val serverMode: ServerMode,
    val core: Core,
    val plugins: Plugins,
    val server: Server?,
) {
    companion object {
        fun load(serverMode: ServerMode): IMConfiguration {
            val configLocation =
                (getenv("UCLOUD_IM_CONFIG")?.toKString() ?:
                if (fileIsDirectory("/etc/ucloud")) "/etc/ucloud"
                else "${homeDirectory()}/ucloud-im").removeSuffix("/")

            val core = Json.decodeFromString<Core>(
                NativeFile.open("$configLocation/core.json", readOnly = true).readText()
            ).normalize()

            val plugins = Json.decodeFromString<Plugins>(
                NativeFile.open("$configLocation/plugins.json", readOnly = true).readText()
            )

            val server = runCatching {
                Json.decodeFromString<Server>(
                    NativeFile.open("$configLocation/server.json", readOnly = true).readText()
                ).normalize(configLocation)
            }.getOrNull()

            if (server == null && serverMode == ServerMode.Server) {
                throw IllegalStateException("Could not read server section")
            }

            return IMConfiguration(configLocation, serverMode, core, plugins, server)
        }
    }

    @Serializable
    data class Core(
        val providerId: String,
        val certificateFile: String? = null,
        val certificate: String? = null,
        val ipcDirectory: String? = null,
    ) {
        fun normalize(): Core {
            val certificate = if (certificateFile != null) {
                NativeFile.open(certificateFile, readOnly = true).readText()
            } else {
                certificate
            } ?: throw IllegalStateException("No certificate available")

            return copy(
                certificateFile = null,
                certificate = certificate.replace("\n", "")
                    .replace("\r", "")
                    .removePrefix("-----BEGIN PUBLIC KEY-----")
                    .removeSuffix("-----END PUBLIC KEY-----")
                    .chunked(64)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .let { "-----BEGIN PUBLIC KEY-----\n" + it + "\n-----END PUBLIC KEY-----" }
            )
        }
    }

    @Serializable
    data class Plugins(
        val compute: ProductBasedConfiguration? = null,
        val connection: JsonObject? = null,
        val identityMapper: JsonObject? = null,
    )

    @Serializable
    data class Server(
        val refreshToken: String,
        val ucloud: UCloud,
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

        @Serializable
        data class UCloud(val host: String, val scheme: String, val port: Int)
    }
}
