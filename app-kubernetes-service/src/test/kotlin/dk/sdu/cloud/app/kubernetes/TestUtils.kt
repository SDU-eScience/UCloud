package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.Application
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.ApplicationMetadata
import dk.sdu.cloud.app.store.api.ApplicationType
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.NormalizedToolDescription
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
    null,
    isPublic = true
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
    ApplicationType.WEB
)

val application = Application(
    applicationMetadata,
    applicationInvocation
)

val verifiedJobInput = VerifiedJobInput(
    mapOf("param" to StringApplicationParameter("value"))
)

val verifiedJob = VerifiedJob(
    id = "verifiedId",
    name = null,
    owner = "owner",
    application = application,
    backend = "backend",
    nodes = 1,
    maxTime = SimpleDuration(0, 1, 0),
    tasksPerNode = 1,
    reservation = MachineReservation.BURST,
    jobInput = VerifiedJobInput(emptyMap()),
    files = emptySet(),
    _mounts = emptySet(),
    _peers = emptySet(),
    currentState = JobState.SCHEDULED,
    status = "status",
    archiveInCollection = application.metadata.title,
    failedState = null,
    createdAt = 12345678,
    modifiedAt = 123456789
)

val jobVerifiedRequest = verifiedJob

val internalFollowStdStreamsRequest = InternalFollowStdStreamsRequest(
    verifiedJob,
    0,
    100,
    0,
    100
)
