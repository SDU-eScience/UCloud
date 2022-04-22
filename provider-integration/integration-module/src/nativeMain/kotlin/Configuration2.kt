package dk.sdu.cloud

import kotlinx.serialization.Serializable

fun loadConfiguration(configurationDirectory: String) {

}

data class Config(
    val core: Core?,
    val server: Server?,
    val plugins: Plugins?,
    val products: Products?,
    val frontendProxy: FrontendProxy?,
) {
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
    data class Core(
        val providerId: String,
        val hosts: Hosts,
        val ipc: Ipc? = null,
        val logs: Logs? = null,
    ) {
        @Serializable
        data class Hosts(
            val ucloud: Host,
            val self: Host? = null,
        )

        @Serializable
        data class Ipc(
            val directory: String,
        )

        @Serializable
        data class Logs(
            val directory: String,
        )
    }

    @Serializable
    data class Server(
        val refreshToken: String,
        val network: Network? = null,
        val developmentMode: DevelopmentMode? = null,
    ) {
        @Serializable
        data class Network(
            val listenAddress: String? = null,
            val listenPort: Int? = null,
        )

        @Serializable
        data class DevelopmentMode(
            val predefinedUserInstances: List<UserInstance> = emptyList(),
        ) {
            @Serializable
            data class UserInstance(
                val username: String,
                val userId: Int,
                val port: Int,
            )
        }
    }

    @Serializable
    data class Plugins(
        val connection: Connection? = null,
        val projects: Projects? = null,
        val jobs: Map<String, Jobs>? = null,
        val files: Map<String, Files>? = null,
        val fileCollections: Map<String, FileCollections>? = null,
    ) {
        @Serializable
        sealed class Connection

        @Serializable
        sealed class Projects

        @Serializable
        sealed class Jobs : ProductBased

        @Serializable
        sealed class Files : ProductBased   

        @Serializable
        sealed class FileCollections : ProductBased

        interface ProductBased {
            val matches: String
        }
    }

    @Serializable
    data class Products(
        val compute: Map<String, List<String>>? = null,
        val storage: Map<String, List<String>>? = null,
    )

    @Serializable
    data class FrontendProxy(
        val sharedSecret: String,
        val remote: Host
    )
}

