package dk.sdu.cloud.config

import dk.sdu.cloud.utils.*
import dk.sdu.cloud.accounting.api.ProductType
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

    interface WithYamlTag {
        val tag: YamlLocationTag
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
            override val tag: YamlLocationTag,
            val ucloud: Host,
            val self: Host? = null,
        ) : WithYamlTag

        @Serializable
        data class Ipc(
            override val tag: YamlLocationTag,
            val directory: String,
        ) : WithYamlTag

        @Serializable
        data class Logs(
            override val tag: YamlLocationTag,
            val directory: String,
        ) : WithYamlTag
    }

    @Serializable
    data class Server(
        val refreshToken: YamlString,
        val network: Network? = null,
        val developmentMode: DevelopmentMode? = null,
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument {
        @Serializable
        data class Network(
            override val tag: YamlLocationTag,
            val listenAddress: String? = null,
            val listenPort: Int? = null,
        ) : WithYamlTag

        @Serializable
        data class DevelopmentMode(
            override val tag: YamlLocationTag,
            val predefinedUserInstances: List<UserInstance> = emptyList(),
        ) : WithYamlTag {
            @Serializable
            data class UserInstance(
                override val tag: YamlLocationTag,
                val username: String,
                val userId: Int,
                val port: Int,
            ) : WithYamlTag
        }
    }

    @Serializable
    data class Plugins(
        val connection: Connection? = null,
        val projects: Projects? = null,
        val jobs: Map<YamlString, Jobs>? = null,
        val files: Map<YamlString, Files>? = null,
        val fileCollections: Map<YamlString, FileCollections>? = null,
        val allocations: Map<ProductType, Allocations>? = null,
        @Transient
        override var yamlDocument: String = ""
    ) : WithYamlDocument {
        @Serializable
        sealed class Connection

        @Serializable
        sealed class Projects

        @Serializable
        sealed class Allocations

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
        val compute: Map<String, List<YamlString>>? = null,
        val storage: Map<String, List<YamlString>>? = null,
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
data class Host(val host: String, val scheme: String, val port: Int, val tag: YamlLocationTag = YamlLocationTag(0)) {
    override fun toString() = buildString {
        append(scheme)
        append("://")
        append(host)
        append(":")
        append(port)
    }

    fun toStringOmitDefaultPort(): String {
        val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return if (isDefaultPort) buildString {
            append(scheme)
            append("://")
            append(host)
        } else {
            toString()
        }
    }
}

fun TerminalMessageDsl.yamlDocumentContext(document: String, startOffset: Int, endOffset: Int) {
    var problematicLineNumberStart = 0
    var problematicLineNumberEnd = 0
    var cursor = 0
    val lines = document.split("\n")
    for ((index, line) in lines.withIndex()) {
        if (startOffset in cursor..(cursor + line.length)) {
            // NOTE(Dan): Usually off by one, so move one back to make the error message easier
            problematicLineNumberStart = index - 1 
            if (problematicLineNumberStart < 0) problematicLineNumberStart = 0
        }

        if (endOffset in cursor..(cursor + line.length)) {
            problematicLineNumberEnd = index
        }

        if (startOffset == endOffset && problematicLineNumberStart != problematicLineNumberEnd) {
            // Don't subtract 1 if the two have equal offsets
            problematicLineNumberStart = problematicLineNumberEnd
        }

        cursor += line.length + 1
    }

    val firstVisibleLine = max(0, problematicLineNumberEnd - 5)
    lines.drop(firstVisibleLine).take(10).forEachIndexed { index, line ->
        val lineNo = (firstVisibleLine + index + 1).toString().padStart(4, ' ')

        val isProblematic = firstVisibleLine + index in problematicLineNumberStart..problematicLineNumberEnd
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
        val locationRef = MutableRef(YamlLocationReference())
        return if (text == null) {
            null
        } else {
            try {
                Yaml.decodeFromString(serializer, text, locationRef).also { it.yamlDocument = text }
            } catch (ex: Throwable) {
                val exMsg = ex.message ?: ""
                sendTerminalMessage {
                    red { bold { inline("Configuration error! ") } }
                    code { line("$configDir/$file") }

                    when {
                        ex is YamlException -> {
                            inline("We could not parse the YAML document. This is what the parser told us: ")
                            code { line(ex.message ?: "") }

                            line()

                            line("The error occured approximately here:")
                            yamlDocumentContext(text, ex.location.approximateStart, ex.location.approximateEnd)
                        }

                        ex is SerializationException && exMsg.contains("is not registered for poly") -> {
                            line("It looks like you have requested a configuration block which does not exist.")
                            line()

                            line("The error occured approximately here:")
                            yamlDocumentContext(text, locationRef.value.approximateStart,
                                locationRef.value.approximateEnd)

                            val errorMessageRegex = Regex("Class '(.+)' is not registered for polymorphic " + 
                                "serialization in the scope of '(.+)'\\.")
                            val shortMessage = exMsg.lines()[0]
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
                                    for (alternative in alternatives) { line(" - $alternative")
                                    }
                                } else {
                                    line()
                                    line("'$inputSerial' is not a recognized configuration block.")
                                }
                            }
                        }

                        ex is SerializationException && exMsg.contains("required for type with serial") -> {
                            line("It looks like you are missing some mandatory configuration.")

                            val errorRegex = Regex("Fields? ['\\[](.+)['\\]] (is|are) required for type with serial " +
                                "name '(.+)', but (it was|they were) missing")

                            val matched = errorRegex.matchEntire(exMsg)
                            if (matched != null) {
                                val fieldsMissing = (matched.groups[1]?.value ?: "").split(",").map { it.trim() }
                                val typeName = (matched.groups[3]?.value ?: "").substringAfterLast(".")
                                if (fieldsMissing.isNotEmpty()) {
                                    line()

                                    inline("The properties are missing from the type: ")
                                    code { line(typeName) }
                                    line()

                                    line("These properties are marked as mandatory and must be provided. See the " + 
                                        "documentation for more details.")
                                    for (field in fieldsMissing) {
                                        line("- $field")
                                    }
                                }
                            }

                            line()
                            line("The error occured approximately here:")
                            yamlDocumentContext(text, locationRef.value.approximateStart,
                                locationRef.value.approximateEnd)
                        }

                        else -> {
                            inline("A generic error has occured! ")
                            code { line(ex.message ?: "") }

                            inline("Error type: ")
                            code { line(ex::class.toString()) }

                            line()

                            line("The error occured approximately here:")
                            yamlDocumentContext(text, locationRef.value.approximateStart,
                                locationRef.value.approximateEnd)

                            line()
                            line("Unfortunately, there is not much else we can tell you.")
                        }
                    }
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
