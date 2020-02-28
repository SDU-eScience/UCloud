package dk.sdu.cloud.app.orchestrator.utils

import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.orchestrator.services.VerifiedJobWithAccessToken
import dk.sdu.cloud.file.api.CowWorkspace
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageFile
import io.mockk.mockk

val normAppDesc = Application(
    ApplicationMetadata(
        "name",
        "2.2",
        listOf("Authors"),
        "title",
        "app description",
        null,
        isPublic = true
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

fun verifiedJobForTestGenerator(
    application: Application? = null,
    name: String? = null,
    files: List<ValidatedFileForUpload> = emptyList(),
    id: String = "verifiedId",
    owner: String = "owner",
    nodes: Int = 1,
    tasksPerNode: Int = 1,
    maxTime: SimpleDuration = SimpleDuration(0, 1, 0),
    jobInput: VerifiedJobInput = VerifiedJobInput(emptyMap()),
    backend: String = "backend",
    currentState: JobState = JobState.RUNNING,
    status: String = currentState.name,
    failedState: JobState? = null,
    archiveInCollection: String = normAppDesc.metadata.title,
    workspace: String? = null,
    createdAt: Long = 12345678,
    modifiedAt: Long = 123456789,
    mounts: List<ValidatedFileForUpload>? = null,
    startedAt: Long = 123456789,
    timeLeft: Long = 123456790,
    user: String = owner,
    project: String? = null,
    folderId: String? = null,
    sharedFileSystemMounts: List<SharedFileSystemMount>? = null,
    peers: List<ApplicationPeer>? = null,
    reservation: MachineReservation = MachineReservation.BURST,
    mountMode: MountMode? = null
):VerifiedJob {
    return VerifiedJob(
        application = application ?: normAppDesc,
        name = name,
        files = files,
        id = id,
        owner = owner,
        nodes = nodes,
        tasksPerNode = tasksPerNode,
        maxTime = maxTime,
        jobInput = jobInput,
        backend = backend,
        currentState = currentState,
        status = status,
        failedState = failedState,
        archiveInCollection = archiveInCollection,
        workspace = workspace,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        _mounts = mounts,
        startedAt = startedAt,
        timeLeft = timeLeft,
        user = user,
        project = project,
        folderId = folderId,
        _sharedFileSystemMounts = sharedFileSystemMounts,
        _peers = peers,
        reservation = reservation,
        mountMode = mountMode

    )
}
val verifiedJob = verifiedJobForTestGenerator(currentState = JobState.SCHEDULED)

val verifiedJob2 = verifiedJobForTestGenerator(id = "verifiedId2", currentState = JobState.SCHEDULED)

val verifiedJobWithAccessToken = VerifiedJobWithAccessToken(
    verifiedJob,
    "token",
    "token"
)

val verifiedJobWithAccessToken2 = VerifiedJobWithAccessToken(
    verifiedJob2,
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

val jobStateChangeCancelling = JobStateChange(
    "systemID",
    JobState.CANCELING
)

val sharedFileSystemMountDescription = SharedFileSystemMountDescription("systemID", "path/to/mnt")

val jobWithStatus = JobWithStatus(
    "jobId",
    "nameOfJob",
    "owner",
    JobState.RUNNING,
    "status",
    null,
    1234,
    1234,
    12345,
    20000,
    null,
    normAppDesc.metadata
)

val storageFile = StorageFile(
    FileType.DIRECTORY,
    "path/to",
    1234,
    1234,
    "owner",
    12,
    emptyList(),
    SensitivityLevel.PRIVATE,
    emptySet(),
    "fileID",
    "creator",
    SensitivityLevel.PRIVATE
)

val validatedFileForUpload = ValidatedFileForUpload(
    "fileId",
    storageFile,
    "destinationFileName",
    "destination/path",
    "source/path",
    null
)
