package dk.sdu.cloud.app.services.ssh

import dk.sdu.cloud.app.services.SlurmEvent
import dk.sdu.cloud.app.services.SlurmEventEnded
import dk.sdu.cloud.app.services.SlurmEventFailed
import dk.sdu.cloud.app.services.SlurmEventRunning
import dk.sdu.cloud.app.services.SlurmEventTimeout
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("org.esciencecloud.app.ssh.SlurmStats")

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
