package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.fs.api.SharedFileSystem
import dk.sdu.cloud.app.orchestrator.api.InternalFollowStdStreamsRequest
import dk.sdu.cloud.app.orchestrator.api.JobState
import dk.sdu.cloud.app.orchestrator.api.SharedFileSystemMount
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJobInput
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.ApplicationType
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
import dk.sdu.cloud.app.store.api.ResourceRequirements
import dk.sdu.cloud.app.store.api.SimpleDuration
import dk.sdu.cloud.app.store.api.StringApplicationParameter
import dk.sdu.cloud.app.store.api.Tool
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.api.ToolReference

val normalizedToolDescription = NormalizedToolDescription(
    NameAndVersion("Tool name", "1.1"),
    "DOCKER",
    1,
    1,
    SimpleDuration(1, 0, 0),
    listOf(),
    listOf("Author1", "Author2"),
    "Tool title",
    "Tool description",
    ToolBackend.DOCKER,
    "MIT"
)

val tool = Tool(
    "Owner of tool",
    123456789,
    1234567890,
    normalizedToolDescription
)

val applicationMetadata = ApplicationMetadata(
    "Application name",
    "2.2",
    listOf("Author1", "Author2"),
    "Application title",
    "Application description",
    listOf("Tag1", "Tag2"),
    "Application.com"

)

val applicationInvocation = ApplicationInvocationDescription(
    ToolReference(
        "Tool name",
        "1.1",
        tool
    ),
    listOf(),
    listOf(),
    listOf(),
    ApplicationType.WEB,
    ResourceRequirements()
)

val application = Application(
    applicationMetadata,
    applicationInvocation
)

val verifiedJobInput = VerifiedJobInput(
    mapOf("param" to StringApplicationParameter("value"))
)

val verifiedJob = VerifiedJob(
    application,
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
    failedState = null,
    archiveInCollection = application.metadata.title,
    createdAt = 12345678,
    modifiedAt = 123456789
)

val jobVerifiedRequest = verifiedJob.copy(
    _sharedFileSystemMounts = listOf(SharedFileSystemMount(SharedFileSystem("1", "owner", "kubernetes"), "mountedAt"))
)

val wrongSharedFileSystem = SharedFileSystem("1", "owner", "blabla")

val internalFollowStdStreamsRequest = InternalFollowStdStreamsRequest(
    verifiedJob,
    0,
    100,
    0,
    100
)
