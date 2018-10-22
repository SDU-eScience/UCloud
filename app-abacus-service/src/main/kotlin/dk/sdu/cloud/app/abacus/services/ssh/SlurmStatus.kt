package dk.sdu.cloud.app.abacus.services.ssh

import dk.sdu.cloud.app.abacus.services.SlurmEvent
import dk.sdu.cloud.app.abacus.services.SlurmEventEnded
import dk.sdu.cloud.app.abacus.services.SlurmEventFailed
import dk.sdu.cloud.app.abacus.services.SlurmEventRunning
import dk.sdu.cloud.app.abacus.services.SlurmEventTimeout
import dk.sdu.cloud.app.abacus.services.ssh.SSH.log

private const val MAX_SPLITS = 3

fun SSHConnection.pollSlurmStatus(): List<SlurmEvent> {
    val (_, text) = execWithOutputAsText("sacct -b -P -n")

    return text.lines().mapNotNull {
        if (it.isBlank()) return@mapNotNull null

        val split = it.split('|')
        if (split.size != MAX_SPLITS) {
            log.warn("Unable to parse line: $it")
            null
        } else {
            val jobId = split[0].toLongOrNull() ?: return@mapNotNull null
            val state = split[1]
            val status = split[2]
            val exitCode = status.split(':').firstOrNull()?.toIntOrNull()

            if (exitCode == null) {
                log.warn("Unable to parse exit code for line: $it")
                null
            } else {
                when (state) {
                    "RUNNING" -> SlurmEventRunning(jobId)
                    "TIMEOUT" -> SlurmEventTimeout(jobId)
                    "COMPLETED" -> SlurmEventEnded(jobId)
                    "FAILED" -> SlurmEventFailed(jobId)
                    else -> null
                }
            }
        }
    }
}
