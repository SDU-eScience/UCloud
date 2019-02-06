package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter

object JobData {
    val application = normAppDesc
        .withNameAndVersion("app", "1")
        .withInvocation(listOf(WordInvocationParameter("test")))
        .withOutputFiles(listOf("stdout.txt", "stderr.txt"))

    val job: VerifiedJob = VerifiedJob(
        application,
        emptyList(),
        "someId",
        "someOwner",
        1,
        1,
        SimpleDuration(1, 0, 0),
        VerifiedJobInput(emptyMap()),
        "abacus",
        JobState.TRANSFER_SUCCESS,
        ""
    )
}
