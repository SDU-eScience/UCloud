package dk.sdu.cloud.config

import dk.sdu.cloud.utils.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Transient
import kotlin.math.max
import kotlin.system.exitProcess

// NOTE(Dan): The ConfigSchema contains the raw schema which is parsed directly from several configuration files
// (see `loadConfiguration()` in this file). This schema contains the raw user input data and is not suitable for
// consumption directly by other components. First the configuration must be validated. This configuration is validated
// by `verifyConfiguration()` in `VerifiedConfig.kt` of this package. This function returns a `VerifiedConfig`. If you
// ever need to add additional configuration, then make sure you add the configuration both in the schema _and_ in the
// `VerifiedConfig`.
//
// We keep these two types separate to make it more clear for downstream components which parts of the configuration
// are optional and which simply have computed defaults. The two types may often end up being similar, but is not a
// requirement and the `VerifiedConfig` can choose to do advanced computations based on various factors if needed.
data class ConfigSchema(
    val configurationDirectory: String,
    val core: Core?,
    val server: Server?,
    val plugins: Plugins?,
    val products: Products?,
    val frontendProxy: FrontendProxy?,
) {
    interface WithYamlDocument {
        var yamlDocument: String
    }

    @Serializable
    data class Core(
        val providerId: String,
        val hosts: Hosts,
        val ipc: Ipc? = null,
        val logs: Logs? = null,

        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument {
        @Serializable
        data class Hosts(
            val ucloud: Host,
            val self: Host? = null,
        )

        @Serializable
        data class Ipc(
            val tag: YamlLocationTag,
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
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument {
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
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument {
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
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument

    @Serializable
    data class FrontendProxy(
        val sharedSecret: String,
        val remote: Host,
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument

    companion object {
        const val FILE_CORE = "core.yaml"
        const val FILE_SERVER = "server.yaml"
        const val FILE_PLUGINS = "plugins.yaml"
        const val FILE_PRODUCTS = "products.yaml"
        const val FILE_FRONTEND_PROXY = "frontend_proxy.yaml"
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

fun TerminalMessageDsl.yamlDocumentContext(document: String, offset: Int) {
    var problematicLineNumber = 0
    var cursor = 0
    val lines = document.split("\n")
    for ((index, line) in lines.withIndex()) {
        if (offset in cursor..(cursor + line.length)) {
            problematicLineNumber = index
            break
        }

        cursor += line.length + 1
    }

    val firstVisibleLine = max(0, problematicLineNumber - 5)
    lines.drop(firstVisibleLine).take(10).forEachIndexed { index, line ->
        val lineNo = (firstVisibleLine + index + 1).toString().padStart(4, ' ')

        val isProblematic = firstVisibleLine + index == problematicLineNumber
        if (isProblematic) {
            red {
                inline(lineNo)
                inline("! ")
                line(line.removeSuffix("\r"))
            }
        } else {
            green {
                inline(lineNo)
                inline(": ")
                line(line.removeSuffix("\r"))
            }
        }
    }
}

fun loadConfiguration(): ConfigSchema {
    val configDir = "/etc/ucloud"

    fun <T : ConfigSchema.WithYamlDocument> parse(file: String, serializer: KSerializer<T>): T? {
        val text = runCatching { NativeFile.open("$configDir/$file", readOnly = true).readText() }.getOrNull()
        val locationRef = MutableRef(0)
        return if (text == null) {
            null
        } else {
            try {
                Yaml.decodeFromString(serializer, text, locationRef).also { it.yamlDocument = text }
            } catch (ex: YamlException) {
                sendTerminalMessage {
                    red { bold { inline("Configuration error! ") } }
                    code { line("$configDir/$file") }

                    inline("We could not parse the YAML document. This is what the parser told us: ")
                    code { line(ex.message ?: "") }

                    line()

                    line("The error occured approximately here:")
                    yamlDocumentContext(text, ex.approximateLocation)
                }

                exitProcess(1)
            } catch (ex: SerializationException) {
                val message = ex.message
                if (message != null && message.contains("is not registered for polymorphic serialization")) {
                    sendTerminalMessage {
                        red { bold { inline("Configuration error! ") } }
                        code { line("$configDir/$file") }

                        inline("It looks like you have requested a configuration block which does not exist.")

                        line("The error occured approximately here:")
                        yamlDocumentContext(text, locationRef.value)

                        val errorMessageRegex = Regex("Class '(.+)' is not registered for polymorphic serialization in the scope of '(.+)'\\.")
                        val shortMessage = message.lines()[0]
                        val matched = errorMessageRegex.matchEntire(shortMessage)
                        if (matched != null) {
                            val inputSerial = matched.groups[1]?.value
                            val targetSerial = matched.groups[2]?.value

                            var alternatives: List<String>? = null
                            when (targetSerial) {
                                "Connection" -> alternatives = listOf("OpenIdConnect", "Simple")
                            }

                            if (alternatives != null) {
                                line()
                                line("Perhaps you meant one of the following instead of '$inputSerial':")
                                for (alternative in alternatives) {
                                    line(" - $alternative")
                                }
                            }
                        }
                    }

                    exitProcess(1)
                } else {
                    sendTerminalMessage {
                        red { bold { inline("Configuration error! ") } }
                        code { line("$configDir/$file") }

                        inline("A generic error has occured! ")
                        code { line(ex.message ?: "") }

                        line()

                        line("The error occured approximately here:")
                        yamlDocumentContext(text, locationRef.value)
                    }
                    exitProcess(1)
                }
            } catch (ex: Throwable) {
                sendTerminalMessage {
                    red { bold { inline("Configuration error! ") } }
                    code { line("$configDir/$file") }

                    inline("A generic error has occured! ")
                    code { line(ex.message ?: "") }

                    line()

                    line("The error occured approximately here:")
                    yamlDocumentContext(text, locationRef.value)
                }

                exitProcess(1)
            }
        }
    }

    val core = parse(ConfigSchema.FILE_CORE, ConfigSchema.Core.serializer())

    val server = parse(ConfigSchema.FILE_SERVER, ConfigSchema.Server.serializer())
    val plugins = parse(ConfigSchema.FILE_PLUGINS, ConfigSchema.Plugins.serializer())
    val products = parse(ConfigSchema.FILE_PRODUCTS, ConfigSchema.Products.serializer())
    val frontendProxy = parse(ConfigSchema.FILE_FRONTEND_PROXY, ConfigSchema.FrontendProxy.serializer())

    return ConfigSchema(configDir, core, server, plugins, products, frontendProxy)
}
