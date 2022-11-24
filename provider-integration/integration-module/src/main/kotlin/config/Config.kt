package dk.sdu.cloud.config

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import dk.sdu.cloud.utils.*
import dk.sdu.cloud.accounting.api.ProductType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import java.io.File
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
@Serializable
data class ConfigSchema(
    val configurationDirectory: String,
    val core: Core?,
    val server: Server?,
    val plugins: Plugins?,
    val products: Products?,
    val frontendProxy: FrontendProxy?,
) {
    @Serializable
    data class Core(
        val providerId: String,
        val hosts: Hosts,
        val ipc: Ipc? = null,
        val logs: Logs? = null,
        val launchRealUserInstances: Boolean = true,
        val allowRootMode: Boolean = false,
        val developmentMode: Boolean? = null,
        val cors: Cors? = null,

        // NOTE(Dan): Some setups with docker compose doesn't correctly handle file permissions. We have this option to
        // just disable the insecure file check. This setting only works in development mode.
        val disableInsecureFileCheckIUnderstandThatThisIsABadIdeaButSomeDevEnvironmentsAreBuggy: Boolean = false,
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
            val directory: String? = null,
            val trace: List<Tracer>? = emptyList(),
        ) {
            enum class Tracer {
                SHELL
            }
        }

        @Serializable
        data class Cors(
            val allowHosts: List<String>? = null
        )
    }

    @Serializable
    data class Server(
        val refreshToken: String,
        val network: Network? = null,
        val developmentMode: DevelopmentMode? = null,
        val database: Database? = null,
        val envoy: Envoy? = null,
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

        @Serializable
        sealed class Database {
            @Serializable
            @SerialName("Embedded")
            data class Embedded(
                val directory: String,
                // NOTE(Dan): Set to 0 for a random port
                val port: Int = 5432
            ) : Database()

            @Serializable
            @SerialName("External")
            data class External(
                val hostname: String,
                val port: Int? = null,
                val username: String,
                val password: String,
                val database: String
            ) : Database()
        }

        @Serializable
        data class Envoy(
            val executable: String? = null,
            val directory: String,
            val downstreamTls: Boolean = false,
            val funceWrapper: Boolean = true,
        )
    }

    @Serializable
    data class Plugins(
        val connection: Connection? = null,
        val projects: Projects? = null,
        val jobs: Map<String, Jobs>? = null,
        val files: Map<String, Files>? = null,
        val fileCollections: Map<String, FileCollections>? = null,
        val ingresses: Map<String, Ingresses>? = null,
        val publicIps: Map<String, PublicIPs>? = null,
        val licenses: Map<String, Licenses>? = null,
        val shares: Map<String, Shares>? = null,
        val allocations: Map<AllocationsProductType, Allocations>? = null,
    ) {
        enum class AllocationsProductType(val type: ProductType?) {
            STORAGE(ProductType.STORAGE),
            COMPUTE(ProductType.COMPUTE),
            INGRESS(ProductType.INGRESS),
            LICENSE(ProductType.LICENSE),
            NETWORK_IP(ProductType.NETWORK_IP),
            ALL(null)
        }

        interface WithAutoInstallSshKey {
            val installSshKeys: Boolean
        }

        @Serializable
        sealed class Connection {
            @Serializable
            @SerialName("UCloud")
            data class UCloud(
                val redirectTo: String,
                val extensions: Extensions = Extensions(),

                // NOTE(Dan): The message signing protocol directly requires safe authentication between end-user and provider
                // directly. The UCloud connection plugin provides no such thing and implicitly trusts UCloud. This trust makes
                // message signing completely useless. As a result, message signing should only be turned on for the purposes of
                // testing the implementation in development.
                val insecureMessageSigningForDevelopmentPurposesOnly: Boolean = false,
                override val installSshKeys: Boolean = true,
            ): Connection(), WithAutoInstallSshKey {
                @Serializable
                data class Extensions(
                    val onConnectionComplete: String? = null
                )
            }

            @Serializable
            @SerialName("Ticket")
            data class Ticket(
                override val installSshKeys: Boolean = true,
            ) : Connection(), WithAutoInstallSshKey

            @Serializable
            @SerialName("OpenIdConnect")
            data class OpenIdConnect(
                @Deprecated("Replaced with signing block")
                val certificate: String? = null,
                val mappingTimeToLive: Ttl,
                val endpoints: Endpoints,
                val client: Client,
                val extensions: Extensions,
                val redirectUrl: String? = null,
                val requireSigning: Boolean = false,
                val signing: Signing? = null,
                override val installSshKeys: Boolean = true,
                val experimental: Experimental = Experimental(),
            ) : Connection(), WithAutoInstallSshKey {
                @Serializable
                data class Ttl(
                    val days: Int = 0,
                    val hours: Int = 0,
                    val minutes: Int = 0,
                    val seconds: Int = 0,
                )

                @Serializable
                data class Endpoints(
                    val auth: String,
                    val token: String,
                )

                @Serializable
                data class Client(
                    val id: String,
                    val secret: String
                )

                @Serializable
                data class Extensions(
                    val onConnectionComplete: String,
                )

                @Serializable
                data class Signing(
                    val algorithm: SignatureType,
                    val key: String,
                    val issuer: String? = null,
                )

                enum class SignatureType {
                    // NOTE(Dan): This is incomplete
                    RS256,
                    ES256
                }

                @Serializable
                data class Experimental(
                    val tokenToUse: TokenToUse = TokenToUse.id_token
                )

                enum class TokenToUse {
                    // NOTE(Dan): The spec says you should use this
                    id_token,

                    // NOTE(Dan): ...and not this one.
                    access_token
                }
            }
        }

        @Serializable
        sealed class Projects {
            @Serializable
            @SerialName("Simple")
            data class Simple(
                val unixGroupNamespace: Int,
                val extensions: Extensions,
            ) : Projects() {
                @Serializable
                data class Extensions(
                    val all: String? = null,

                    val projectRenamed: String? = null,

                    val membersAddedToProject: String? = null,
                    val membersRemovedFromProject: String? = null,

                    val membersAddedToGroup: String? = null,
                    val membersRemovedFromGroup: String? = null,

                    val projectArchived: String? = null,
                    val projectUnarchived: String? = null,

                    val roleChanged: String? = null,

                    val groupCreated: String? = null,
                    val groupRenamed: String? = null,
                    val groupDeleted: String? = null,
                )
            }

            @Serializable
            @SerialName("Puhuri")
            data class Puhuri(
                val endpoint: String,
                val apiToken: String,
                val customerId: String,
                val offeringId: String,
                val planId: String,
            ) : Projects()
        }

        @Serializable
        sealed class Allocations {
            @Serializable
            @SerialName("Extension")
            data class Extension(
                val extensions: Extensions,
            ) : ConfigSchema.Plugins.Allocations() {
                @Serializable
                data class Extensions(
                    val onAllocationTotal: String,
                    val onAllocationSingle: String,
                    val onSynchronizationTotal: String,
                    val onSynchronizationSingle: String,
                )
            }

            @Serializable
            @SerialName("Puhuri")
            class Puhuri : Allocations()
        }

        @Serializable
        sealed class Jobs : ProductBased {
            @Serializable
            @SerialName("Slurm")
            data class Slurm(
                override val matches: String,
                val partition: String,
                val useFakeMemoryAllocations: Boolean = false,
                val accountMapper: AccountMapper = AccountMapper.None(),
                val modifySlurmConf: String? = "/etc/slurm/slurm.conf",
                val web: Web = Web.None(),
                val udocker: UDocker = UDocker(),
                val terminal: Terminal = Terminal.Ssh()
            ) : Jobs() {
                @Serializable
                sealed class AccountMapper {
                    // This mapper will always return no preferred accounts, which will cause slurm to always use the default.
                    // This implicitly turns off accounting of all jobs which are not known to UCloud/IM.
                    @Serializable
                    @SerialName("None")
                    class None : AccountMapper()

                    @Serializable
                    @SerialName("Extension")
                    data class Extension(val extension: String) : AccountMapper()
                }

                @Serializable
                sealed class Web {
                    @Serializable
                    @SerialName("None")
                    class None : Web()

                    @Serializable
                    @SerialName("Simple")
                    class Simple(
                        val domainPrefix: String,
                        val domainSuffix: String,
                    ) : Web()
                }

                @Serializable
                data class UDocker(
                    val enabled: Boolean = false,
                    val execMode: ExecMode = ExecMode.P2
                ) {
                    enum class ExecMode {
                        P1,
                        P2,
                        F1,
                        F2,
                        F3,
                        F4,
                        R1,
                        R2,
                        R3,
                        S1
                    }
                }

                @Serializable
                sealed class Terminal {
                    abstract val enabled: Boolean

                    @Serializable
                    @SerialName("SSH")
                    data class Ssh(
                        override val enabled: Boolean = true,
                        val generateSshKeys: Boolean = false,
                    ) : Terminal()

                    @Serializable
                    @SerialName("Slurm")
                    data class Slurm(
                        override val enabled: Boolean = true
                    ) : Terminal()
                }
            }

            @Serializable
            @SerialName("Puhuri")
            class Puhuri(override val matches: String) : Jobs()

            @Serializable
            @SerialName("UCloud")
            class UCloud(
                override val matches: String,
                val kubeConfig: String? = null,
                val kubeSvcOverride: String? = null,
                val useMachineSelector: Boolean = false,
                val systemReservedCpuMillis: Int = 0,
                val systemReservedMemMegabytes: Int = 0,
                val forceMinimumReservation: Boolean = false,
                val nodeToleration: TolerationKeyAndValue? = null,
                val namespace: String = "app-kubernetes",
                val scheduler: Scheduler = Scheduler.Volcano,
                val categoryToSelector: Map<String, String> = emptyMap(),
                val fakeIpMount: Boolean = false,
                val ssh: Ssh? = null,
                val usePortForwarding: Boolean = false,
            ) : Jobs() {
                @Serializable
                data class TolerationKeyAndValue(val key: String, val value: String)

                @Serializable
                data class SshSubnet(
                    val iface: String,
                    val privateCidr: String,
                    val publicHostname: String,
                    val portMin: Int,
                    val portMax: Int,
                )

                @Serializable
                data class Ssh(
                    val subnets: List<SshSubnet>,
                )

                enum class Scheduler {
                    Volcano,
                    Pods
                }
            }
        }

        @Serializable
        sealed class Files : ProductBased {
            @Serializable
            @SerialName("Posix")
            data class Posix(
                override val matches: String,
            ) : Files()

            @Serializable
            @SerialName("Puhuri")
            class Puhuri(override val matches: String) : Files()

            @Serializable
            @SerialName("UCloud")
            class UCloud(
                override val matches: String,
                val mountLocation: String,
                val useCephStats: Boolean = false,
                val accountingEnabled: Boolean = false,
            ) : Files()
        }

        @Serializable
        sealed class FileCollections : ProductBased {
            @Serializable
            @SerialName("Posix")
            data class Posix(
                override val matches: String,
                val extensions: Extensions = Extensions(),
                @Deprecated("replace with extensions.accounting")
                val accounting: String? = null,
            ) : ConfigSchema.Plugins.FileCollections() {
                @Serializable
                data class Extensions(
                    val driveLocator: String? = null,
                    val accounting: String? = null,
                    @Deprecated("replace with driveLocator")
                    val additionalCollections: String? = null,
                )
            }

            @Serializable
            @SerialName("Puhuri")
            class Puhuri(override val matches: String) : FileCollections()

            @Serializable
            @SerialName("UCloud")
            class UCloud(override val matches: String) : FileCollections()
        }

        @Serializable
        sealed class Ingresses : ProductBased {
            @Serializable
            @SerialName("UCloud")
            class UCloud(
                override val matches: String,
                val domainPrefix: String,
                val domainSuffix: String,
            ) : Ingresses()
        }

        @Serializable
        sealed class PublicIPs : ProductBased {
            @Serializable
            @SerialName("UCloud")
            class UCloud(
                override val matches: String,
                val iface: String,
                val gatewayCidr: String? = null
            ) : PublicIPs()
        }

        @Serializable
        sealed class Licenses : ProductBased {
            @Serializable
            @SerialName("Generic")
            class Generic(
                override val matches: String,
            ) : Licenses()
        }

        @Serializable
        sealed class Shares : ProductBased {
            @Serializable
            @SerialName("UCloud")
            class UCloud(
                override val matches: String
            ) : Shares()
        }

        interface ProductBased {
            val matches: String
        }
    }

    @Serializable
    data class Products(
        val compute: Map<String, List<ConfigProduct.Compute>>? = null,
        val storage: Map<String, List<ConfigProduct.Storage>>? = null,
        val ingress: Map<String, List<ConfigProduct.Ingress>>? = null,
        val publicIps: Map<String, List<ConfigProduct.NetworkIP>>? = null,
        val licenses: Map<String, List<ConfigProduct.License>>? = null,
    )

    @Serializable
    data class FrontendProxy(
        val sharedSecret: String,
        val remote: Host,
    )

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

fun loadConfiguration(): ConfigSchema {
    val potentialAltConfigDir = runCatching { File("/etc/ucloud/configdir.txt").readText().trim() }.getOrNull()
    val configDir = potentialAltConfigDir ?: "/etc/ucloud"
    val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
            strictMode = false,
        )
    )

    fun <T> parse(file: String, serializer: KSerializer<T>): T? {
        val text = runCatching { NativeFile.open("$configDir/$file", readOnly = true).readText() }.getOrNull()
        return if (text == null) {
            null
        } else {
            try {
                yaml.decodeFromString(serializer, text)
            } catch (ex: Throwable) {
                val exMsg = ex.message ?: ""
                sendTerminalMessage {
                    red { bold { inline("Configuration error! ") } }
                    code { line("$configDir/$file") }

                    when {
                        ex is SerializationException && exMsg.contains("is not registered for poly") -> {
                            line("It looks like you have requested a configuration block which does not exist.")
                            line()
                            println(exMsg)

                            val errorMessageRegex = Regex(
                                "Class '(.+)' is not registered for polymorphic " +
                                        "serialization in the scope of '(.+)'\\."
                            )
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
                                    for (alternative in alternatives) {
                                        line(" - $alternative")
                                    }
                                } else {
                                    line()
                                    line("'$inputSerial' is not a recognized configuration block.")
                                }
                            }
                        }

                        ex is SerializationException && exMsg.contains("required for type with serial") -> {
                            line("It looks like you are missing some mandatory configuration.")

                            val errorRegex = Regex(
                                "Fields? ['\\[](.+)['\\]] (is|are) required for type with serial " +
                                        "name '(.+)', but (it was|they were) missing"
                            )

                            val matched = errorRegex.matchEntire(exMsg)
                            if (matched != null) {
                                val fieldsMissing = (matched.groups[1]?.value ?: "").split(",").map { it.trim() }
                                val typeName = (matched.groups[3]?.value ?: "").substringAfterLast(".")
                                if (fieldsMissing.isNotEmpty()) {
                                    line()

                                    inline("The properties are missing from the type: ")
                                    code { line(typeName) }
                                    line()

                                    line(
                                        "These properties are marked as mandatory and must be provided. See the " +
                                                "documentation for more details."
                                    )
                                    for (field in fieldsMissing) {
                                        line("- $field")
                                    }
                                }
                            }

                            line()
                        }

                        else -> {
                            inline("A generic error has occured! ")
                            code { line(ex.message ?: "") }

                            inline("Error type: ")
                            code { line(ex::class.toString()) }

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
