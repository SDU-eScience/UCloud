package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.HttpStatusCode
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

        @JsonDeserialize(`as` = NameAndVersionImpl::class)
        val tool: NameAndVersion,
        val authors: List<String>,
        val title: String,
        val description: String,
        invocation: List<Any>,
        val parameters: Map<String, ApplicationParameter<*>> = emptyMap(),
        outputFileGlobs: List<String> = emptyList(),

        applicationType: String? = null,
        val vnc: VncDescription? = null,
        val web: WebDescription? = null,
        val container: ContainerDescription? = null,
        environment: Map<String, Any>? = null,
        val allowAdditionalMounts: Boolean? = null,
        val allowAdditionalPeers: Boolean? = null,
        val allowMultiNode: Boolean? = false
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

            parameters.forEach { name, parameter -> parameter.name = name }

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
                null
            )

            val invocation = ApplicationInvocationDescription(
                ToolReference(tool.name, tool.version, null),
                invocation,
                parameters.values.toList(),
                outputFileGlobs,
                applicationType,
                vnc = vnc,
                web = web,
                container = container,
                environment = environment,
                allowAdditionalMounts = allowAdditionalMounts,
                allowAdditionalPeers = allowAdditionalPeers,
                allowMultiNode = allowMultiNode ?: false
            )

            return Application(metadata, invocation)
        }
    }
}

sealed class ApplicationVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class DuplicateDefinition(type: String, definitions: List<String>) :
        ApplicationVerificationException(
            "Duplicate definition of $type. " +
                    "Duplicates where: ${definitions.joinToString(", ")}",
            HttpStatusCode.BadRequest
        )

    class BadValue(parameter: String, why: String) :
        ApplicationVerificationException("Parameter '$parameter' received a bad value. $why", HttpStatusCode.BadRequest)

    class BadVariableReference(where: String, name: String) :
        ApplicationVerificationException(
            "Variable referenced at $where with name '$name' could not be resolved",
            HttpStatusCode.BadRequest
        )
}

enum class ApplicationType {
    BATCH,
    VNC,
    WEB
}

