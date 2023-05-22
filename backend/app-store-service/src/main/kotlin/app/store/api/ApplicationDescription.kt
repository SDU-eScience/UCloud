package dk.sdu.cloud.app.store.api

import app.store.api.ApplicationParameterYaml
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.json.*
import kotlin.reflect.KProperty0

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "application"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApplicationDescription.V1::class, name = "v1")
)
sealed class ApplicationDescription(val application: String) {
    abstract fun normalize(): Application

    class V1(
        val name: String,
        val version: String,

        val tool: NameAndVersion,
        val authors: List<String>,
        val title: String,
        val description: String,
        invocation: List<Any>,
        val parameters: Map<String, ApplicationParameterYaml> = emptyMap(),
        outputFileGlobs: List<String> = emptyList(),
        applicationType: String? = null,
        val vnc: VncDescription? = null,
        val web: WebDescription? = null,
        val ssh: SshDescription? = null,
        val container: ContainerDescription? = null,
        environment: Map<String, Any>? = null,
        val allowAdditionalMounts: Boolean? = null,
        val allowAdditionalPeers: Boolean? = null,
        val allowMultiNode: Boolean? = false,
        val allowPublicIp: Boolean? = false,
        val allowPublicLink: Boolean? = false,
        var fileExtensions: List<String> = emptyList(),
        var licenseServers: List<String> = emptyList(),
        val website: String? = null,
        val modules: ModulesSection? = null,
    ) : ApplicationDescription("v1") {
        val invocation: List<InvocationParameter>
        val environment: Map<String, InvocationParameter>?

        val outputFileGlobs: List<String>
        val applicationType: ApplicationType
        init {
            ::title.requireNotBlank()
            ::title.disallowCharacters('\n', '/')
            ::title.requireSize(maxSize = 1024)

            ::name.requireNotBlank()
            ::name.disallowCharacters('\n')
            ::name.requireSize(maxSize = 255)

            ::version.requireNotBlank()
            ::version.disallowCharacters('\n')
            ::version.requireSize(maxSize = 255)

            if (authors.isEmpty()) throw ToolVerificationException.BadValue(::authors.name, "Authors is empty")

            val badAuthorIndex = authors.indexOfFirst { it.contains("\n") }
            if (badAuthorIndex != -1) {
                throw ApplicationVerificationException.BadValue("author[$badAuthorIndex]", "Cannot contain new lines")
            }

            val duplicateGlobs = outputFileGlobs.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicateGlobs.isNotEmpty()) {
                throw ApplicationVerificationException.DuplicateDefinition("outputFileGlobs", duplicateGlobs.toList())
            }

            this.outputFileGlobs = outputFileGlobs.let {
                val result = it.toMutableSet()

                // Add default list
                result.add("stdout.txt")
                result.add("stderr.txt")

                result
            }.toList()

            this.applicationType = applicationType?.let { ApplicationType.valueOf(it) } ?: ApplicationType.BATCH

            parameters.forEach { (name, parameter) -> parameter.name = name }

            // Verify default values
            for (param in parameters.values) {
                if (param.defaultValue == null) continue

                when (param) {
                    is ApplicationParameterYaml.Bool -> {
                        when (val value = param.defaultValue) {
                            is Boolean -> continue
                            is Map<*, *> -> {
                                value["value"] as? Boolean ?:
                                    throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                            else ->
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                        }
                    }

                    is ApplicationParameterYaml.Enumeration -> {
                        when (val value = param.defaultValue) {
                            is String -> continue
                            is Map<*, *> -> {
                                value["value"] as? String ?:
                                    throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                            else ->
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                        }
                    }
                    is ApplicationParameterYaml.FloatingPoint -> {
                        val normalizedDefault = when (val value = param.defaultValue) {
                            is Double -> {
                                value as? Double ?: throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                            is Map<*, *> -> {
                                value["value"] as? Double ?: throw ApplicationVerificationException.BadDefaultValue(
                                    param.name
                                )
                            }
                            else -> {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }

                        if (param.min != null) {
                            if (normalizedDefault < param.min) {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }
                        if (param.max != null) {
                            if (normalizedDefault > param.max) {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }
                    }
                    is ApplicationParameterYaml.Ingress -> continue
                    is ApplicationParameterYaml.InputDirectory,
                    is ApplicationParameterYaml.InputFile -> {
                        when (val value = param.defaultValue) {
                            is String -> continue
                            is Map<*, *> -> {
                                value["path"] as? String ?: throw ApplicationVerificationException.BadDefaultValue(
                                    param.name
                                )
                            }
                            else ->
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                        }
                    }
                    is ApplicationParameterYaml.Integer -> {
                        val normalizedDefault = when (val value = param.defaultValue) {
                            is Int -> {
                                value as? Int ?: throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                            is Map<*, *> -> {
                                value["value"] as? Int ?: throw ApplicationVerificationException.BadDefaultValue(
                                    param.name
                                )
                            }
                            else -> {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }

                        if (param.min != null) {
                            if (normalizedDefault < param.min) {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }
                        if (param.max != null) {
                            if (normalizedDefault > param.max) {
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                            }
                        }
                    }
                    is ApplicationParameterYaml.LicenseServer -> continue
                    is ApplicationParameterYaml.NetworkIP -> continue
                    is ApplicationParameterYaml.Peer -> continue
                    is ApplicationParameterYaml.Text,
                    is ApplicationParameterYaml.TextArea -> {
                        when (val value = param.defaultValue) {
                            is String -> continue
                            is Map<*, *> -> {
                                value["value"] as? String ?: throw ApplicationVerificationException.BadDefaultValue(
                                    param.name
                                )
                            }
                            else ->
                                throw ApplicationVerificationException.BadDefaultValue(param.name)
                        }
                    }
                }

            }

            this.invocation = invocation.mapIndexed { index, it ->
                val parameterName = "invocation[$index]"
                parseInvocationParameter(it, parameterName)
            }

            this.invocation.forEachIndexed { index, it ->
                val parameterName = "invocation[$index]"
                val variables = when (it) {
                    is VariableInvocationParameter -> {
                        it.variableNames
                    }

                    is BooleanFlagParameter -> {
                        listOf(it.variableName)
                    }

                    else -> return@forEachIndexed
                }
                val missingVariable = variables.find { !parameters.containsKey(it) }
                if (missingVariable != null) {
                    throw ApplicationVerificationException.BadVariableReference(parameterName, missingVariable)
                }
            }

            this.environment = environment?.map { (name, param) ->
                name to parseInvocationParameter(param, name)
            }?.toMap()
        }

        private fun parseInvocationParameter(
            param: Any,
            parameterName: String
        ): InvocationParameter {
            return when (param) {
                is String -> {
                    WordInvocationParameter(param)
                }

                is Int, is Long, is Boolean, is Double, is Float, is Short -> {
                    WordInvocationParameter(param.toString())
                }

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val map = param as Map<String, Any?>

                    val type = map["type"] ?: throw ApplicationVerificationException.BadValue(
                        parameterName,
                        "Missing 'type' tag"
                    )

                    when (type) {
                        "var" -> {
                            val vars = (map["vars"] ?: throw ApplicationVerificationException.BadValue(
                                parameterName,
                                "missing 'vars'"
                            ))

                            val variableNames =
                                (vars as? List<*>)?.map { it.toString() } ?: listOf(vars.toString())
                            val prefixGlobal = map["prefixGlobal"]?.toString() ?: ""
                            val suffixGlobal = map["suffixGlobal"]?.toString() ?: ""
                            val prefixVariable = map["prefixVariable"]?.toString() ?: ""
                            val suffixVariable = map["suffixVariable"]?.toString() ?: ""
                            val isPrefixVariablePartOfArg = map["isPrefixVariablePartOfArg"] as? Boolean ?: false
                            val isSuffixVariablePartOfArg = map["isSuffixVariablePartOfArg"] as? Boolean ?: false

                            VariableInvocationParameter(
                                variableNames,
                                prefixGlobal,
                                suffixGlobal,
                                prefixVariable,
                                suffixVariable,
                                isPrefixVariablePartOfArg,
                                isSuffixVariablePartOfArg
                            )
                        }

                        "flag" -> {
                            val variable = map["var"] ?: throw ApplicationVerificationException.BadValue(
                                parameterName,
                                "missing 'var'"
                            )

                            val flag = map["flag"] ?: throw ApplicationVerificationException.BadValue(
                                parameterName,
                                "Missing 'flag'"
                            )

                            BooleanFlagParameter(variable.toString(), flag.toString())
                        }

                        "env" -> {
                            val variable = map["var"] ?: throw ApplicationVerificationException.BadValue(
                                parameterName,
                                "missing 'var'"
                            )

                            EnvironmentVariableParameter(variable.toString())
                        }

                        else -> throw ApplicationVerificationException.BadValue(parameterName, "Unknown type")
                    }
                }

                else -> throw ApplicationVerificationException.BadValue(parameterName, "Bad value")
            }
        }

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

        override fun normalize(): Application {
            val metadata = ApplicationMetadata(
                name,
                version,
                authors,
                title,
                description,
                website,
                // TODO: should this always be false by default?
                false
            )

            val normalizedParameters = parameters.values.map { param ->
                when (param) {
                    is ApplicationParameterYaml.Bool -> {
                        ApplicationParameter.Bool(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when (val value = param.defaultValue) {
                                            is Boolean -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue =
                                                    value["value"] as? Boolean ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }

                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description,
                            param.trueValue,
                            param.falseValue
                        )
                    }
                    is ApplicationParameterYaml.Enumeration -> {
                        ApplicationParameter.Enumeration(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when (val value = param.defaultValue) {
                                            is String -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue =
                                                    value["value"] as? String ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }

                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description,
                            param.options.map { ApplicationParameter.EnumOption(it.name, it.value) }
                        )
                    }
                    is ApplicationParameterYaml.FloatingPoint -> {
                        ApplicationParameter.FloatingPoint(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when(val value = param.defaultValue) {
                                            is Double -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue = value["value"] as? Double ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }
                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description,
                            param.min,
                            param.max,
                            param.step,
                            param.unitName
                        )
                    }
                    is ApplicationParameterYaml.Ingress -> {
                        ApplicationParameter.Ingress(
                            param.name,
                            param.title,
                            param.description
                        )
                    }
                    is ApplicationParameterYaml.InputDirectory -> {
                        ApplicationParameter.InputDirectory(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "path" to when(val value = param.defaultValue) {
                                            is String -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue = value["path"] as? String ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }
                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description
                        )
                    }
                    is ApplicationParameterYaml.InputFile -> {
                        ApplicationParameter.InputFile(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "path" to when(val value = param.defaultValue) {
                                            is String -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue = value["path"] as? String ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }
                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description,
                        )
                    }
                    is ApplicationParameterYaml.Integer -> {
                        ApplicationParameter.Integer(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when (val value = param.defaultValue) {
                                            is Int -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue = value["value"] as? Int ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }
                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description,
                            param.min,
                            param.max,
                            param.step,
                            param.unitName
                        )
                    }
                    is ApplicationParameterYaml.LicenseServer -> {
                        ApplicationParameter.LicenseServer(
                            param.name,
                            param.title,
                            param.optional,
                            param.description,
                            param.tagged
                        )
                    }
                    is ApplicationParameterYaml.NetworkIP -> {
                        ApplicationParameter.NetworkIP(
                            param.name,
                            param.title,
                            param.description
                        )
                    }
                    is ApplicationParameterYaml.Peer -> {
                        ApplicationParameter.Peer(
                            param.name,
                            param.title,
                            param.description,
                            param.suggestedApplication
                        )
                    }
                    is ApplicationParameterYaml.Text -> {
                        ApplicationParameter.Text(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when(val value = param.defaultValue) {
                                            is String -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue = value["value"] as? String ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }
                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description
                        )
                    }
                    is ApplicationParameterYaml.TextArea -> {
                        ApplicationParameter.TextArea(
                            param.name,
                            param.optional,
                            if (param.defaultValue == null) { JsonNull } else {
                                JsonObject(
                                    mapOf(
                                        "value" to when (val value = param.defaultValue) {
                                            is String -> JsonPrimitive(value)
                                            is Map<*, *> -> {
                                                val defaultValue =
                                                    value["value"] as? String ?: error("bad default value")
                                                JsonPrimitive(defaultValue)
                                            }

                                            else -> error("bad default value")
                                        }
                                    )
                                )
                            },
                            param.title,
                            param.description
                        )
                    }
                }
            }

            val invocation = ApplicationInvocationDescription(
                ToolReference(tool.name, tool.version, null),
                invocation,
                normalizedParameters,
                outputFileGlobs,
                applicationType,
                vnc = vnc,
                web = web,
                ssh = ssh,
                container = container,
                environment = environment,
                allowAdditionalMounts = allowAdditionalMounts,
                allowAdditionalPeers = allowAdditionalPeers,
                allowMultiNode = allowMultiNode ?: false,
                allowPublicIp = allowPublicIp,
                allowPublicLink = allowPublicLink ?: (applicationType == ApplicationType.WEB),
                fileExtensions = fileExtensions,
                licenseServers = licenseServers,
                modules = modules,
            )

            return Application(metadata, invocation)
        }
    }
}
