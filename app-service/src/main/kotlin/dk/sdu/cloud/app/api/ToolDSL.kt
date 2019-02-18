package dk.sdu.cloud.app.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import io.ktor.http.HttpStatusCode
import org.slf4j.Logger

private const val FIELD_MAX_LENGTH = 255

enum class ToolBackend {
    SINGULARITY,
    UDOCKER
}

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
            if (name.length > FIELD_MAX_LENGTH)
                throw ToolVerificationException.BadValue(::name.name, "Name is too long")
            if (version.length > FIELD_MAX_LENGTH)
                throw ToolVerificationException.BadValue(::version.name, "Version is too long")
            if (title.length > FIELD_MAX_LENGTH)
                throw ToolVerificationException.BadValue(::title.name, "Title is too long")

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

    companion object : Loggable {
        override val log: Logger = logger()
    }
}

sealed class ToolVerificationException(why: String, httpStatusCode: HttpStatusCode) :
    RPCException(why, httpStatusCode) {
    class BadValue(parameter: String, reason: String) :
        ToolVerificationException("Parameter '$parameter' received a bad value. $reason", HttpStatusCode.BadRequest)
}
