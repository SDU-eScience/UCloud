package dk.sdu.cloud.app.orchestrator.utils

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.VerifiedJobWithAccessToken
import io.mockk.mockk

val normAppDesc = Application(
    ApplicationMetadata(
        "name",
        "2.2",
        listOf("Authors"),
        "title",
        "app description",
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

val normAppDesc3 = normAppDesc
    .withInvocation(
        listOf(
            VariableInvocationParameter(listOf("int"), prefixVariable = "--int "),
            VariableInvocationParameter(listOf("great"), prefixVariable = "--great "),
            VariableInvocationParameter(listOf("missing"), prefixGlobal = "--missing ")
        )
    )
    .withParameters(
        listOf(
            ApplicationParameter.Integer("int"),
            ApplicationParameter.Text("great", true),
            ApplicationParameter.Integer("missing", true)
        )
    )

fun Application.withNameAndVersion(name: String, version: String): Application {
    return copy(
        metadata = normAppDesc.metadata.copy(
            name = name,
            version = version
        )
    )
}

fun Application.withNameAndVersionAndTitle(name: String, version: String, title: String): Application {
    return copy(
        metadata = normAppDesc.metadata.copy(
            name = name,
            version = version,
            title = title
        )
    )
}

fun Application.withInvocation(invocation: List<InvocationParameter>): Application = copy(
    invocation = this.invocation.copy(
        invocation = invocation
    )
)

fun Application.withParameters(parameters: List<ApplicationParameter<*>>): Application = copy(
    invocation = this.invocation.copy(
        parameters = parameters
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
    ToolBackend.DOCKER,
    "MIT"
)

val normTool = Tool("", 0L, 0L, normToolDesc)

val verifiedJob = VerifiedJob(
    normAppDesc,
    null,
    emptyList(),
    "verifiedId",
    "owner",
    1,
    1,
    SimpleDuration(0, 1, 0),
    VerifiedJobInput(emptyMap()),
    "backend",
    JobState.SCHEDULED,
    "scheduled",
    null,
    archiveInCollection = normAppDesc.metadata.title,
    createdAt = 12345678,
    modifiedAt = 123456789
)

val verifiedJobWithAccessToken = VerifiedJobWithAccessToken(
    verifiedJob,
    "token",
    "token"
)

val startJobRequest = StartJobRequest(
    NameAndVersion("name", "2.2"),
    null,
    emptyMap(),
    1,
    1,
    SimpleDuration(1, 0, 0)
)
