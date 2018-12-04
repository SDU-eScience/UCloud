package dk.sdu.cloud.app.abacus.services.ssh

import dk.sdu.cloud.app.abacus.services.SlurmPollAgent
import dk.sdu.cloud.app.api.SimpleDuration

private const val SUPPOSED_NUMBER_OF_SPLITS = 3

fun SSHConnection.slurmJobInfo(jobId: Long): SimpleDuration {
    if (jobId == SlurmPollAgent.SLURM_ID_SKIP) return SimpleDuration(0, 1, 0)

    val (exit, output) = execWithOutputAsText("""sacct --format="elapsed" -s cd -s f -s to -n -X -P -j $jobId""")
    if (exit != 0) throw IllegalStateException("Slurm job info returned $exit with output: $output")
    val timeSplit = output.trim().split(":")
    if (timeSplit.size != SUPPOSED_NUMBER_OF_SPLITS) throw IllegalStateException("Bad output: $output")

    val hours = timeSplit[0].toIntOrNull() ?: throw IllegalStateException("Bad hours: $output")
    val minutes = timeSplit[1].toIntOrNull() ?: throw IllegalStateException("Bad minutes: $output")
    val seconds = timeSplit[2].toIntOrNull() ?: throw IllegalStateException("Bad seconds: $output")

    return SimpleDuration(hours, minutes, seconds)
}
