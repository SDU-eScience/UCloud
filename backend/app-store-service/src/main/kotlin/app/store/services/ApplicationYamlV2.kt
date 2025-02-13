package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Time
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.reflect.KProperty0

data class ApplicationYamlV2(
    val name: String,
    val version: String,
    val software: Software,
    val features: Features? = null,
    val parameters: Map<String, Parameter>? = null,
    val sbatch: Map<String, String>? = null,
    val invocation: String,
    val environment: Map<String, String>? = null,
    val web: Web? = null,
    val vnc: Vnc? = null,
    val extensions: List<String>? = null,
) : ApplicationYaml("v2") {
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = NativeSoftware::class, name = "Native"),
        JsonSubTypes.Type(value = ContainerSoftware::class, name = "Container"),
    )
    sealed class Software

    data class NativeSoftware(
        val load: List<ApplicationToLoad>
    ) : Software() {
        data class ApplicationToLoad(val name: String, val version: String)
    }

    data class ContainerSoftware(
        val image: String,
    ) : Software()

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = Parameter.File::class, name = "File"),
        JsonSubTypes.Type(value = Parameter.Directory::class, name = "Directory"),
        JsonSubTypes.Type(value = Parameter.License::class, name = "License"),
        JsonSubTypes.Type(value = Parameter.Job::class, name = "Job"),
        JsonSubTypes.Type(value = Parameter.PublicIP::class, name = "PublicIP"),
        JsonSubTypes.Type(value = Parameter.Integer::class, name = "Integer"),
        JsonSubTypes.Type(value = Parameter.FloatingPoint::class, name = "FloatingPoint"),
        JsonSubTypes.Type(value = Parameter.Bool::class, name = "Boolean"),
        JsonSubTypes.Type(value = Parameter.Text::class, name = "Text"),
        JsonSubTypes.Type(value = Parameter.TextArea::class, name = "TextArea"),
        JsonSubTypes.Type(value = Parameter.Enumeration::class, name = "Enumeration"),
        JsonSubTypes.Type(value = Parameter.Workflow::class, name = "Workflow"),
    )
    sealed class Parameter {
        abstract val title: String
        abstract val description: String
        abstract val optional: Boolean

        data class File(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
        ) : Parameter()

        data class Directory(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
        ) : Parameter()

        data class License(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
        ) : Parameter()

        data class Job(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
        ) : Parameter()

        data class PublicIP(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
        ) : Parameter()

        data class Integer(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: Long? = null,
            val min: Long? = null,
            val max: Long? = null,
            val step: Long? = null,
        ) : Parameter()

        data class FloatingPoint(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: Double? = null,
            val min: Double? = null,
            val max: Double? = null,
            val step: Double? = null,
        ) : Parameter()

        data class Bool(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: Boolean? = null,
        ) : Parameter()

        data class Text(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: String? = null,
        ) : Parameter()

        data class TextArea(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: String? = null,
        ) : Parameter()

        data class EnumOption(
            val title: String,
            val value: String,
        )

        data class Enumeration(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val defaultValue: String? = null, // references the value
            val options: List<EnumOption>,
        ) : Parameter()

        data class Workflow(
            override val title: String,
            override val description: String,
            override val optional: Boolean = true,
            val init: String? = null,
            val job: String? = null,
            val readme: String? = null,
            val parameters: Map<String, Parameter> = emptyMap(),
        ) : Parameter()
    }

    data class Features(
        val multiNode: Boolean = false,
        val forkable: Boolean? = null,
        val requireFork: Boolean? = null,
        val links: Boolean? = null,
        val ipAddresses: Boolean? = null,
        val folders: Boolean? = null,
        val jobLinking: Boolean? = null,
    )

    data class Web(
        val enabled: Boolean,
        val port: Int? = null,
    )

    data class Vnc(
        val enabled: Boolean,
        val port: Int? = null,
        val password: String? = null,
    )

    private fun KProperty0<String>.requireNotBlank() {
        if (get().isBlank()) throw ApplicationVerificationException.BadValue(name, "Cannot be empty")
    }

    private fun KProperty0<String>.disallowCharacters(vararg disallowed: Char) {
        val value = get()
        val match = disallowed.find { value.contains(it) }
        if (match != null) {
            throw ApplicationVerificationException.BadValue(name, "Cannot contain '$match'")
        }
    }

    private fun KProperty0<String>.requireSize(minSize: Int = 1, maxSize: Int) {
        val value = get()
        if (value.length < minSize) throw ApplicationVerificationException.BadValue(
            name,
            "Most be at least $minSize characters long"
        )

        if (value.length > maxSize) throw ApplicationVerificationException.BadValue(
            name,
            "Cannot be longer than $maxSize characters long"
        )
    }

    override fun normalizeToAppAndTool(): Pair<Application, Tool?> {
        ::name.requireNotBlank()
        ::name.disallowCharacters('\n')
        ::name.requireSize(maxSize = 255)

        ::version.requireNotBlank()
        ::version.disallowCharacters('\n')
        ::version.requireSize(maxSize = 255)

        if (parameters != null && parameters.size > 2048) {
            throw ApplicationVerificationException.BadValue("parameters", "Too many parameters supplied")
        }

        for ((name, param) in (parameters ?: emptyMap())) {
            ::name.requireNotBlank()
            ::name.disallowCharacters('\n')
            ::name.requireSize(maxSize = 255)

            param::title.requireNotBlank()
            param::title.requireSize(maxSize = 512)

            param::description.requireNotBlank()
            param::description.requireSize(maxSize = 1024 * 8)

            if (name.startsWith(injectedPrefix)) {
                throw ApplicationVerificationException.BadValue(name, "Parameters must not start with _injected_")
            }

            when (param) {
                is Parameter.Bool,
                is Parameter.Directory,
                is Parameter.File,
                is Parameter.PublicIP,
                is Parameter.License,
                is Parameter.Text,
                is Parameter.TextArea,
                is Parameter.Workflow,
                is Parameter.Job -> {
                    // Nothing else to do
                }

                is Parameter.Enumeration -> {
                    if (param.options.isEmpty()) {
                        throw ApplicationVerificationException.BadValue("options", "Options of an enumeration must not be empty!")
                    }

                    val default = param.defaultValue
                    if (default != null) {
                        if (param.options.none { it.value == default }) {
                            throw ApplicationVerificationException.BadValue(
                                "defaultValue",
                                "The defaultValue of an enumeration '$default' was not found in the options"
                            )
                        }
                    }

                    for (opt in param.options) {
                        opt::title.requireNotBlank()
                        opt::title.disallowCharacters('\n')
                        opt::title.requireSize(maxSize = 255)

                        opt::value.requireNotBlank()
                        opt::value.disallowCharacters('\n')
                        opt::value.requireSize(maxSize = 255)
                    }
                }

                is Parameter.FloatingPoint -> {
                    val default = param.defaultValue
                    if (default != null) {
                        val min = param.min
                        if (min != null && default < min) {
                            throw ApplicationVerificationException.BadValue(
                                "defaultValue",
                                "The default value of $name must not be lower than the minimum"
                            )
                        }

                        val max = param.max
                        if (max != null && default > max) {
                            throw ApplicationVerificationException.BadValue(
                                "defaultValue",
                                "The default value of $name must not be higher than the maximum"
                            )
                        }
                    }
                }

                is Parameter.Integer -> {
                    val default = param.defaultValue
                    if (default != null) {
                        val min = param.min
                        if (min != null && default < min) {
                            throw ApplicationVerificationException.BadValue(
                                "defaultValue",
                                "The default value of $name must not be lower than the minimum"
                            )
                        }

                        val max = param.max
                        if (max != null && default > max) {
                            throw ApplicationVerificationException.BadValue(
                                "defaultValue",
                                "The default value of $name must not be higher than the maximum"
                            )
                        }
                    }
                }
            }
        }

        // TODO There is currently not much we can do about the validation of Jinja templates since we do not wish
        //  to, currently, introduce this dependency in the Kotlin codebase.

        val tool = when(software) {
            is NativeSoftware -> {
                Tool(
                    "_ucloud",
                    Time.now(),
                    Time.now(),
                    NormalizedToolDescription(
                        info = NameAndVersion(name, version),
                        container = null,
                        defaultNumberOfNodes = 1,
                        defaultTimeAllocation = SimpleDuration(1, 0, 0),
                        requiredModules = emptyList(),
                        authors = listOf("UCloud"),
                        title = name,
                        description = "",
                        backend = ToolBackend.NATIVE,
                        license = "",
                        image = null,
                        supportedProviders = null,
                        loadInstructions = ToolLoadInstructions.Native(
                            software.load.map { ToolLoadInstructions.NativeApplication(it.name, it.version) }
                        ),
                    )
                )
            }

            is ContainerSoftware -> {
                Tool(
                    "_ucloud",
                    Time.now(),
                    Time.now(),
                    NormalizedToolDescription(
                        info = NameAndVersion(name, version),
                        container = software.image,
                        defaultNumberOfNodes = 1,
                        defaultTimeAllocation = SimpleDuration(1, 0, 0),
                        requiredModules = emptyList(),
                        authors = listOf("UCloud"),
                        title = name,
                        description = "",
                        backend = ToolBackend.DOCKER,
                        license = "",
                        image = software.image,
                        supportedProviders = null,
                        loadInstructions = null,
                    )
                )
            }
        }

        val appType = when {
            // TODO Support both?
            web != null && web.enabled -> ApplicationType.WEB
            vnc != null && vnc.enabled -> ApplicationType.VNC
            else -> ApplicationType.BATCH
        }
        val app = Application(
            ApplicationMetadata(
                name,
                version,
                authors = listOf("UCloud"),
                title = name,
                description = "",
                website = null,
                public = false,
                flavorName = null,
                createdAt = Time.now(),
            ),
            ApplicationInvocationDescription(
                tool = ToolReference(name, version, tool),
                invocation = listOf(JinjaInvocationParameter(invocation)),
                parameters = (parameters ?: emptyMap()).map { (name, param) ->
                    mapApplicationParameter(param, name)
                },
                outputFileGlobs = listOf("*"),
                applicationType = appType,
                vnc = vnc?.takeIf { it.enabled }?.let {
                    VncDescription(it.password, it.port.takeIf { p -> p != 0 } ?: 5900)
                },
                web = web?.takeIf { it.enabled }?.let {
                    WebDescription(
                        it.port.takeIf { p -> p != 0 } ?: 80,
                    )
                },
                // TODO(Dan): Add ssh
                ssh = null,

                // TODO(Dan): License server hints (if they are even used?)
                licenseServers = emptyList(),

                // TODO(Dan): Add docker based apps
                container = if (software is ContainerSoftware) {
                    ContainerDescription(
                        changeWorkingDirectory = true,
                        runAsRoot = true,
                        runAsRealUser = false
                    )
                } else {
                    null
                },
                modules = null,

                environment = (environment ?: emptyMap()).map { (key, value) ->
                    key to JinjaInvocationParameter(value)
                }.toMap(),
                allowAdditionalMounts =
                    features?.folders == true || (features?.folders == null && appType != ApplicationType.BATCH),
                allowAdditionalPeers =
                    features?.jobLinking == true || (features?.jobLinking == null && appType != ApplicationType.BATCH),
                allowMultiNode = features?.multiNode == true,
                allowPublicIp = features?.ipAddresses == true,
                allowPublicLink = features?.links == true,
                fileExtensions = extensions ?: emptyList(),
                sbatch = (sbatch ?: emptyMap()).map { (k, v) ->
                    k to JinjaInvocationParameter(v)
                }.toMap()
            )
        )

        return Pair(app, tool)
    }

    private fun mapApplicationParameter(
        param: Parameter,
        name: String
    ): ApplicationParameter = when (param) {
        is Parameter.Bool -> ApplicationParameter.Bool(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description
        )

        is Parameter.Directory -> ApplicationParameter.InputDirectory(
            name,
            param.optional,
            null,
            param.title,
            param.description,
        )

        is Parameter.Enumeration -> ApplicationParameter.Enumeration(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description,
            param.options.map { ApplicationParameter.EnumOption(it.value, it.title) }
        )

        is Parameter.File -> ApplicationParameter.InputFile(
            name,
            param.optional,
            null,
            param.title,
            param.description
        )

        is Parameter.FloatingPoint -> ApplicationParameter.FloatingPoint(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description,
            param.min,
            param.max,
            param.step,
            null
        )

        is Parameter.Integer -> ApplicationParameter.Integer(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description,
            param.min,
            param.max,
            param.step,
            null
        )

        is Parameter.Job -> ApplicationParameter.Peer(
            name,
            param.title,
            param.description,
            null,
        )

        is Parameter.License -> ApplicationParameter.LicenseServer(
            name,
            param.title,
            param.optional,
            param.description,
            emptyList(),
        )

        is Parameter.PublicIP -> ApplicationParameter.NetworkIP(
            name,
            param.title,
            param.description,
        )

        is Parameter.Text -> ApplicationParameter.Text(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description,
        )

        is Parameter.TextArea -> ApplicationParameter.TextArea(
            name,
            param.optional,
            param.defaultValue?.let { JsonPrimitive(it) },
            param.title,
            param.description,
        )

        is Parameter.Workflow -> ApplicationParameter.Workflow(
            name,
            param.title,
            param.description,
            Workflow.Specification(
                "",
                WorkflowLanguage.JINJA2,
                param.init,
                param.job,
                param.parameters.map { mapApplicationParameter(it.value, it.key) },
                param.readme,
            ).let { defaultMapper.encodeToJsonElement(it) },
            param.optional,
        )
    }
}
