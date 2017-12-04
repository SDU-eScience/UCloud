package org.esciencecloud.abc.services.ssh

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.esciencecloud.abc.HPCConfig
import org.esciencecloud.abc.services.SlurmEvent
import org.esciencecloud.abc.services.SlurmEventEnded
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

private val log = LoggerFactory.getLogger("org.esciencecloud.abc.ssh.SlurmStats")
private val ABACUS_ZONE = ZoneId.of("Europe/Copenhagen")

fun SSHConnection.pollSlurmStatus(sinceWhen: ZonedDateTime): List<SlurmEvent> {
    fun Int.asTwoDigits() = toString().padStart(2, '0')
    // Just in case this software will be run on hardware in another zone, we convert all to zone where HPC is located

    // TODO This might be a bit surprising
    // Go back a bit, we don't know if we are in sync with the HPC server
    val then = sinceWhen.withZoneSameInstant(ABACUS_ZONE).minusMinutes(1L)
    val since = "${then.year}-${then.monthValue.asTwoDigits()}-${then.dayOfMonth.asTwoDigits()}" +
            "T${then.hour.asTwoDigits()}:${then.minute.asTwoDigits()}:${then.second.asTwoDigits()}"

    val (_, text) = execWithOutputAsText("sacct -b -P -n -S $since")

    return text.lines().mapNotNull {
        if (it.isBlank()) return@mapNotNull null

        val split = it.split('|')
        if (split.size != 3) {
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
                if (state == "COMPLETED") SlurmEventEnded(jobId, "job.sh", Duration.ZERO, state, exitCode)
                else null
            }
        }
    }
}

fun main(args: Array<String>) {
    val since = ZonedDateTime.now().minusHours(3)
    val mapper = jacksonObjectMapper()
    val conf = mapper.readValue<HPCConfig>(File("hpc_conf.json"))
    val pool = SSHConnectionPool(conf.ssh)
    pool.use {
        pollSlurmStatus(since).forEach { println(it) }
    }
}