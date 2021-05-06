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
    val core: Core,
    val plugins: Plugins,
) {
    companion object {
        fun load(): IMConfiguration {
            val configLocation = getenv("UCLOUD_IM_CONFIG")?.toKString() ?: "${homeDirectory()}/ucloud-im"

            val core = Json.decodeFromString<Core>(
                NativeFile.open("$configLocation/core.json", readOnly = true).readText()
            ).normalize()

            val plugins = Json.decodeFromString<Plugins>(
                NativeFile.open("$configLocation/plugins.json", readOnly = true).readText()
            )

            return IMConfiguration(core, plugins)
        }
    }

    @Serializable
    data class Core(
        val providerId: String,
        val refreshToken: String,
        val certificateFile: String?,
        val certificate: String?,
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
        val compute: JsonObject,
    )
}
