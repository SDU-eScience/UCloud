package dk.sdu.cloud.app.kubernetes

import dk.sdu.cloud.app.api.*
import dk.sdu.cloud.app.kubernetes.services.PodService
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.initializeMicro
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import kotlinx.coroutines.runBlocking
import java.util.*

val normToolDesc = NormalizedToolDescription(
    NameAndVersion("tool", "1.0.0"),
    "functions/figlet",
    2,
    2,
    SimpleDuration(1, 0, 0),
    listOf(""),
    listOf("Author"),
    "title",
    "description",
    ToolBackend.SINGULARITY
)
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
        ToolReference("tool", "1.0.0", Tool("", 0L, 0L, normToolDesc)),
        listOf(
            WordInvocationParameter("figlet"),
            VariableInvocationParameter(listOf("text"))
        ),
        listOf(
            ApplicationParameter.Text("text")
        ),
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

fun Application.withParameters(parameters: List<ApplicationParameter<*>>): Application = copy(
    invocation = this.invocation.copy(
        parameters = parameters
    )
)

fun Application.withOutputFiles(fileGlobs: List<String>): Application = copy(
    invocation = this.invocation.copy(
        outputFileGlobs = fileGlobs
    )
)


fun main() = runBlocking {
    /*
    val micro = initializeMicro()
    ClientMock.mockCallSuccess(ComputationCallbackDescriptions.completed, Unit)
    ClientMock.mockCallSuccess(ComputationCallbackDescriptions.requestStateChange, Unit)

    val service = PodService(DefaultKubernetesClient(), ClientMock.authenticatedClient)
    val job = VerifiedJob(
        normAppDesc,
        emptyList(),
        "foobar-${UUID.randomUUID()}",
        "foobar",
        1,
        1,
        SimpleDuration(1, 0, 0),
        VerifiedJobInput(mapOf("text" to StringApplicationParameter("Hello, world!"))),
        "kubernetes",
        JobState.VALIDATED,
        "",
        ownerUid = 1337L,
        archiveInCollection = "f"
    )

    service.create(job)
    */
}
