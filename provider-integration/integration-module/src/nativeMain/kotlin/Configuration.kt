package dk.sdu.cloud

import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.homeDirectory
import dk.sdu.cloud.utils.readText
import kotlinx.cinterop.toKString
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import platform.posix.getenv

class IMConfiguration(
    val serverMode: ServerMode,
    val core: Core,
    val plugins: Plugins,
    val server: Server?,
) {
    companion object {
        fun load(serverMode: ServerMode): IMConfiguration {
            val configLocation =
                (getenv("UCLOUD_IM_CONFIG")?.toKString() ?: "${homeDirectory()}/ucloud-im").removeSuffix("/")

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

            return IMConfiguration(serverMode, core, plugins, server)
        }
    }

    @Serializable
    data class Core(
        val providerId: String,
        val certificateFile: String? = null,
        val certificate: String? = null,
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
        val compute: JsonObject? = null,
        val connection: JsonObject? = null,
    )

    @Serializable
    data class Server(
        val refreshToken: String,
        val dbFile: String = ""
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
}
