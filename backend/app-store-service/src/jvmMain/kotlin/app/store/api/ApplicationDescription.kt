package dk.sdu.cloud.app.store.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.calls.RPCException
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KProperty0

@Serializable
sealed class ApplicationDescription(val application: String) {
    abstract fun normalize(): Application

    @Serializable
    data class V1(
        val name: String,
        val version: String,

        val tool: NameAndVersion,
        val authors: List<String>,
        val title: String,
        val description: String,
        val invocation: List<JsonElement>,
        val parameters: Map<String, ApplicationParameter> = emptyMap(),
        val outputFileGlobs: List<String> = emptyList(),
        val applicationType: String? = null,
        val vnc: VncDescription? = null,
        val web: WebDescription? = null,
        val container: ContainerDescription? = null,
        val environment: Map<String, JsonElement>? = null,
        val allowAdditionalMounts: Boolean? = null,
        val allowAdditionalPeers: Boolean? = null,
        val allowMultiNode: Boolean? = false,
        val allowPublicIp: Boolean? = false,
        var fileExtensions: List<String> = emptyList(),
        var licenseServers: List<String> = emptyList(),
        val website: String? = null
    ) : ApplicationDescription("v1") {
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

            parameters.forEach { name, parameter -> parameter.name = name }
        }

        private fun parseInvocationParameter(
            param: JsonElement,
            parameterName: String
        ): InvocationParameter {
            return when (param) {
                is JsonPrimitive -> WordInvocationParameter(param.content)

                is JsonObject -> {
                    val map = param
                    val type = map["type"] as? JsonPrimitive ?: throw ApplicationVerificationException.BadValue(
                        parameterName,
                        "Missing 'type' tag"
                    )

                    when (type.content) {
                        "var" -> {
                            val vars = (map["vars"] ?: throw ApplicationVerificationException.BadValue(
                                parameterName,
                                "missing 'vars'"
                            ))

                            val variableNames = (vars as? JsonArray)?.mapNotNull {
                                (it as? JsonPrimitive)?.content
                            } ?: run {
                                listOf(
                                    (vars as? JsonPrimitive)?.content
                                        ?: throw ApplicationVerificationException.BadValue(
                                            parameterName,
                                            "bad value given for vars"
                                        )
                                )
                            }
                            val prefixGlobal = (map["prefixGlobal"] as? JsonPrimitive)?.content ?: ""
                            val suffixGlobal = (map["suffixGlobal"] as? JsonPrimitive)?.content ?: ""
                            val prefixVariable = (map["prefixVariable"] as? JsonPrimitive)?.content ?: ""
                            val suffixVariable = (map["suffixVariable"] as? JsonPrimitive)?.content ?: ""
                            val isPrefixVariablePartOfArg =
                                (map["isPrefixVariablePartOfArg"] as? JsonPrimitive)?.content?.equals("true") ?: false
                            val isSuffixVariablePartOfArg =
                                (map["isSuffixVariablePartOfArg"] as? JsonPrimitive)?.content?.equals("true") ?: false

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
                            val variable = map["var"] as? JsonPrimitive
                                ?: throw ApplicationVerificationException.BadValue(parameterName, "missing 'var'")

                            val flag = map["flag"] as? JsonPrimitive
                                ?: throw ApplicationVerificationException.BadValue(parameterName, "Missing 'flag'")

                            BooleanFlagParameter(variable.content, flag.content)
                        }

                        "env" -> {
                            val variable = map["var"] as? JsonPrimitive
                                ?: throw ApplicationVerificationException.BadValue(parameterName, "missing 'var'")

                            EnvironmentVariableParameter(variable.content)
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

            parameters.forEach{ (_, appParam) ->
                if (appParam is ApplicationParameter.Enumeration) {
                    when (val defaultValue = appParam.defaultValue) {
                        is JsonObject -> {
                            val map = defaultValue
                            val type = map["type"] as? JsonPrimitive ?: throw ApplicationVerificationException.BadValue(
                                defaultValue.toString(),
                                "Missing 'type' tag"
                            )
                            if (type.content != "enumeration") {
                                throw ApplicationVerificationException.BadValue(map.toString(), "Should be enumeration")
                            }
                            val value = map["value"] as? JsonPrimitive ?: throw ApplicationVerificationException.BadValue(
                                defaultValue.toString(),
                                "Missing 'value'"
                            )
                            if (appParam.options.find { it.value == value.content } == null ) {
                                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "default value does not match posibilities")
                            }
                        }
                        else -> throw ApplicationVerificationException.BadValue(defaultValue.toString(), "Bad value")
                    }
                }
            }

            val duplicateGlobs = outputFileGlobs.groupBy { it }.filter { it.value.size > 1 }.keys
            if (duplicateGlobs.isNotEmpty()) {
                throw ApplicationVerificationException.DuplicateDefinition("outputFileGlobs", duplicateGlobs.toList())
            }

            val newOutputFileGlobs = outputFileGlobs.let {
                val result = it.toMutableSet()

                // Add default list
                result.add("stdout.txt")
                result.add("stderr.txt")

                result
            }.toList()


            val newInvocation = invocation.mapIndexed { index, it ->
                val parameterName = "invocation[$index]"
                parseInvocationParameter(it, parameterName)
            }

            newInvocation.forEachIndexed { index, it ->
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

            val newApplicationType = applicationType?.let { ApplicationType.valueOf(it) } ?: ApplicationType.BATCH
            val newEnvironment = environment?.map { (name, param) ->
                name to parseInvocationParameter(param, name)
            }?.toMap()

            val invocation = ApplicationInvocationDescription(
                ToolReference(tool.name, tool.version, null),
                newInvocation,
                parameters.values.toList(),
                newOutputFileGlobs,
                newApplicationType,
                vnc = vnc,
                web = web,
                container = container,
                environment = newEnvironment,
                allowAdditionalMounts = allowAdditionalMounts,
                allowAdditionalPeers = allowAdditionalPeers,
                allowPublicIp = allowPublicIp ?: false,
                allowMultiNode = allowMultiNode ?: false,
                fileExtensions = fileExtensions,
                licenseServers = licenseServers
            )

            return Application(metadata, invocation)
        }
    }
}
