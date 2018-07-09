package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.app.services.BooleanFlagParameter
import dk.sdu.cloud.app.services.InvocationParameter
import dk.sdu.cloud.app.services.VariableInvocationParameter
import dk.sdu.cloud.app.services.WordInvocationParameter
import kotlin.reflect.KProperty0

data class ApplicationSummary(
    val tool: NameAndVersion,
    val info: NameAndVersion,
    val prettyName: String,
    val authors: List<String>,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String
)

data class ApplicationWithOptionalDependencies(
    val application: NormalizedApplicationDescription,
    val tool: ToolDescription?
)

data class NormalizedApplicationDescription(
    val tool: NameAndVersion,
    val info: NameAndVersion,
    val authors: List<String>,
    val prettyName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String,
    val invocation: List<InvocationParameter>,
    // TODO We cannot have duplicates on param name!
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>
) {
    fun toSummary(): ApplicationSummary = ApplicationSummary(
        tool, info, prettyName, authors, createdAt, modifiedAt, description
    )
}

// Note: It is currently assumed that validation is done in layers above
data class NewApplication(
    val createdAt: Long,
    val modifiedAt: Long,
    val description: NewNormalizedApplicationDescription
)
data class NewNormalizedApplicationDescription(
    val info: NameAndVersion,
    val tool: NameAndVersion,
    val authors: List<String>,
    val title: String,
    val description: String,
    val invocation: List<InvocationParameter>,
    val parameters: List<ApplicationParameter<*>>,
    val outputFileGlobs: List<String>
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
    abstract fun normalize(): NewNormalizedApplicationDescription

    class V1(
        val name: String,
        val version: String,

        val tool: NameAndVersion,
        val authors: List<String>,
        val title: String,
        val description: String,
        invocation: List<Any>,
        val parameters: Map<String, ApplicationParameter<*>>,
        outputFileGlobs: List<String>
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
                                val variable = map["variable"] ?: throw ApplicationVerificationException.BadValue(
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

        override fun normalize(): NewNormalizedApplicationDescription {
            return NewNormalizedApplicationDescription(
                NameAndVersion(name, version),
                tool,
                authors,
                title,
                description,
                invocation,
                parameters.values.toList(),
                outputFileGlobs
            )
        }
    }
}

sealed class ApplicationVerificationException(why: String) : RuntimeException(why) {
    class DuplicateDefinition(type: String, definitions: List<String>) :
        ApplicationVerificationException(
            "Duplicate definition of $type. " +
                    "Duplicates where: ${definitions.joinToString(", ")}"
        )

    class BadValue(parameter: String, why: String) :
        ApplicationVerificationException("Parameter '$parameter' received a bad value. $why")

    class BadVariableReference(where: String, name: String) :
        ApplicationVerificationException("Variable referenced at $where with name '$name' could not be resolved")
}

enum class ToolBackend {
    SINGULARITY,
    UDOCKER
}

data class NameAndVersion(val name: String, val version: String) {
    override fun toString() = "$name@$version"
}

data class ToolDescription(
    val info: NameAndVersion,
    val container: String,
    val defaultNumberOfNodes: Int,
    val defaultTasksPerNode: Int,
    val defaultMaxTime: SimpleDuration,
    val requiredModules: List<String>,
    val authors: List<String>,
    val prettyName: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val description: String,
    val backend: ToolBackend = ToolBackend.SINGULARITY
)
