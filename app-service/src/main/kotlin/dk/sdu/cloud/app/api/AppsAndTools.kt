package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.service.RPCException
import io.ktor.http.HttpStatusCode
import kotlin.reflect.KProperty0

// Note: It is currently assumed that validation is done in layers above
data class Application(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedApplicationDescription,
    val tool: Tool
)

//TODO Contains duplicate data: Info, Tool, Tags. Issue #307
data class NormalizedApplicationDescription(
    val info: NameAndVersion,
    val tool: NameAndVersion,
    val authors: List<String>,
    val title: String,
    val description: String,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>,
    val tags: List<String> = emptyList()
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "application"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ApplicationDescription.V1::class, name = "v1")
)
sealed class ApplicationDescription(val application: String) {
    abstract fun normalize(): NormalizedApplicationDescription

    class V1(
        val name: String,
        val version: String,

        val tool: NameAndVersion,
        val authors: List<String>,
        val title: String,
        val description: String,
        invocation: List<Any>,
        val parameters: Map<String, ApplicationParameter<*>> = emptyMap(),
        outputFileGlobs: List<String> = emptyList(),

        val tags: List<String> = emptyList()

    ) : ApplicationDescription("v1") {
        val invocation: List<InvocationParameter>

        val outputFileGlobs: List<String>

        init {
            ::title.requireNotBlank()
            ::title.disallowCharacters('\n')
            ::title.requireSize(maxSize = 1024)

            ::name.requireNotBlank()
            ::name.disallowCharacters('\n')
            ::name.requireSize(maxSize = 255)

            ::version.requireNotBlank()
            ::version.disallowCharacters('\n')
            ::version.requireSize(maxSize = 255)

            tags.forEach {
                if (it.isBlank()) {
                    throw ApplicationVerificationException.BadValue(name, "Cannot be empty")
                }
            }
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
                val result = it.toMutableList()

                // Remove defaults if exists
                result.remove("stdout.txt")
                result.remove("stderr.txt")

                // Add default list
                result.add("stdout.txt")
                result.add("stderr.txt")

                result
            }

            parameters.forEach { name, parameter -> parameter.name = name }

            this.invocation = invocation.mapIndexed { index, it ->
                val parameterName = "invocation[$index]"
                when (it) {
                    is String -> {
                        WordInvocationParameter(it)
                    }

                    is Int, is Long, is Boolean, is Double, is Float, is Short -> {
                        WordInvocationParameter(it.toString())
                    }

                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val map = it as Map<String, Any?>

                        val type = map["type"] ?: throw ApplicationVerificationException.BadValue(
                            parameterName,
                            "Missing 'type' tag"
                        )

                        when (type) {
                            "var" -> {
                                val vars = (map["vars"] ?: throw ApplicationVerificationException.BadValue(
                                    parameterName,
                                    "missing variableNames"
                                ))

                                val variableNames =
                                    (vars as? List<*>)?.map { it.toString() } ?: listOf(vars.toString())
                                val prefixGlobal = map["prefixGlobal"]?.toString() ?: ""
                                val suffixGlobal = map["suffixGlobal"]?.toString() ?: ""
                                val prefixVariable = map["prefixVariable"]?.toString() ?: ""
                                val variableSeparator = map["variableSeparator"]?.toString() ?: " "

                                VariableInvocationParameter(
                                    variableNames,
                                    prefixGlobal,
                                    suffixGlobal,
                                    prefixVariable,
                                    suffixGlobal,
                                    variableSeparator
                                )
                            }

                            "flag" -> {
                                val variable = map["var"] ?: throw ApplicationVerificationException.BadValue(
                                    parameterName,
                                    "missing 'variable'"
                                )

                                val flag = map["flag"] ?: throw ApplicationVerificationException.BadValue(
                                    parameterName,
                                    "Missing 'flag'"
                                )

                                BooleanFlagParameter(variable.toString(), flag.toString())
                            }

                            else -> throw ApplicationVerificationException.BadValue(parameterName, "Unknown type")
                        }
                    }

                    else -> throw ApplicationVerificationException.BadValue(parameterName, "Bad value")
                }
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

        override fun normalize(): NormalizedApplicationDescription {
            return NormalizedApplicationDescription(
                NameAndVersion(name, version),
                tool,
                authors,
                title,
                description,
                invocation,
                parameters.values.toList(),
                outputFileGlobs,
                tags
            )
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

enum class ToolBackend {
    SINGULARITY,
    UDOCKER
}

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}

data class NormalizedToolDescription(
    val info: NameAndVersion,
    val container: String,
    val defaultNumberOfNodes: Int,
    val defaultTasksPerNode: Int,
    val defaultMaxTime: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val title: String,
    val description: String,
    val backend: ToolBackend
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "tool"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ToolDescription.V1::class, name = "v1")
)
sealed class ToolDescription(val tool: String) {
    abstract fun normalize(): NormalizedToolDescription

    class V1(
        val name: String,
        val version: String,
        val title: String,
        val container: String,
        val backend: ToolBackend,
        val authors: List<String>,
        val defaultNumberOfNodes: Int = 1,
        val defaultTasksPerNode: Int = 1,
        val defaultMaxTime: SimpleDuration = SimpleDuration(1, 0, 0),
        val requiredModules: List<String> = emptyList(),
        val description: String = ""
    ) : ToolDescription("v1") {
        init {
            if (name.length > 255) throw ToolVerificationException.BadValue(::name.name, "Name is too long")
            if (version.length > 255) throw ToolVerificationException.BadValue(::version.name, "Version is too long")
            if (title.length > 255) throw ToolVerificationException.BadValue(::title.name, "Title is too long")

            if (name.isBlank()) throw ToolVerificationException.BadValue(::name.name, "Name is blank")
            if (version.isBlank()) throw ToolVerificationException.BadValue(::version.name, "Version is blank")
            if (title.isBlank()) throw ToolVerificationException.BadValue(::title.name, "Title is blank")

            if (name.contains('\n')) throw ToolVerificationException.BadValue(::name.name, "Name contains newlines")
            if (version.contains('\n')) throw ToolVerificationException.BadValue(
                ::version.name,
                "Version contains newlines"
            )
            if (title.contains('\n')) throw ToolVerificationException.BadValue(::title.name, "Title contains newlines")

            if (authors.isEmpty()) throw ToolVerificationException.BadValue(::authors.name, "Authors is empty")
            val badAuthorIndex = authors.indexOfFirst { it.contains("\n") }
            println(badAuthorIndex)
            if (badAuthorIndex != -1) {
                throw ToolVerificationException.BadValue("author[$badAuthorIndex]", "Cannot contain new lines")
            }
        }

        override fun normalize(): NormalizedToolDescription {
            return NormalizedToolDescription(
                NameAndVersion(name, version),
                container,
                defaultNumberOfNodes,
                defaultTasksPerNode,
                defaultMaxTime,
                requiredModules,
                authors,
                title,
                description,
                backend
            )
        }
    }
}

sealed class ToolVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class BadValue(parameter: String, reason: String) :
        ToolVerificationException("Parameter '$parameter' received a bad value. $reason", HttpStatusCode.BadRequest)
}

data class Tool(
    val owner: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NormalizedToolDescription
)