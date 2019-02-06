package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.api.ApplicationMetadata
import dk.sdu.cloud.app.api.InvocationParameter
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.ToolReference
import io.mockk.mockk

val normAppDesc = Application(
    ApplicationMetadata(
        "name",
        "2.2",
        listOf("Authors"),
        "title",
        "app description",
        emptyList(),
        null
    ),
    ApplicationInvocationDescription(
        ToolReference("tool", "1.0.0", null),
        mockk(relaxed = true),
        emptyList(),
        listOf("glob")
    )
)

val normAppDesc2 = normAppDesc.withNameAndVersion("app", "1.2")

fun Application.withNameAndVersion(name: String, version: String): Application {
    return copy(
        metadata = normAppDesc.metadata.copy(
            name = name,
            version = version
        )
    )
}

fun Application.withTags(tags: List<String>): Application = copy(
    metadata = metadata.copy(
        tags = tags
    )
)

fun Application.withInvocation(invocation: List<InvocationParameter>): Application = copy(
    invocation = this.invocation.copy(
        invocation = invocation
    )
)

val normToolDesc = NormalizedToolDescription(
    NameAndVersion("tool", "1.0.0"),
    "container",
    2,
    2,
    SimpleDuration(1, 0, 0),
    listOf(""),
    listOf("Author"),
    "title",
    "description",
    ToolBackend.UDOCKER
)
