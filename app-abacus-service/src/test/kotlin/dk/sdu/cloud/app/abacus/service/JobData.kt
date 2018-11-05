package dk.sdu.cloud.app.abacus.service

import dk.sdu.cloud.app.api.Application
import dk.sdu.cloud.app.api.JobState
import dk.sdu.cloud.app.api.NameAndVersion
import dk.sdu.cloud.app.api.NormalizedApplicationDescription
import dk.sdu.cloud.app.api.NormalizedToolDescription
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.api.Tool
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.VerifiedJobInput
import dk.sdu.cloud.app.api.WordInvocationParameter

object JobData {
    val tool = Tool(
        "appOwner",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedToolDescription(
            NameAndVersion("tool", "1"),
            "container",
            1,
            1,
            SimpleDuration(1, 0, 0),
            emptyList(),
            listOf("Author"),
            "tool",
            "tool",
            ToolBackend.SINGULARITY
        )
    )

    val application = Application(
        "appOwner",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        NormalizedApplicationDescription(
            NameAndVersion("app", "1"),
            tool.description.info,
            listOf("Author"),
            "app",
            "appDescription",
            listOf(WordInvocationParameter("test")),
            emptyList(),
            listOf("stdout.txt", "stderr.txt"),
            emptyList()
        ),
        tool
    )

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
