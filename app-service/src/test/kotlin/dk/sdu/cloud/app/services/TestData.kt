package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.*
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
    ToolBackend.UDOCKER
)

val verifiedJob = VerifiedJob(
    normAppDesc,
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
    archiveInCollection = normAppDesc.metadata.title,
    createdAt = 12345678,
    modifiedAt = 123456789,
    ownerUid = 1337L
)

val verifiedJobWithAccessToken = VerifiedJobWithAccessToken(
    verifiedJob,
    "token"
)

val startJobRequest = StartJobRequest(
    NameAndVersion("name", "2.2"),
    emptyMap(),
    1,
    1,
    SimpleDuration(1, 0, 0),
    ToolBackend.UDOCKER.name
)
