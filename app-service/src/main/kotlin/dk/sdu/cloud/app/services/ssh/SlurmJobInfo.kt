package dk.sdu.cloud.app.services.ssh

import dk.sdu.cloud.app.api.SimpleDuration

fun SSHConnection.slurmJobInfo(jobId: Long): SimpleDuration {
    val (exit, output) = execWithOutputAsText("""sacct --format="elapsed" -s cd -n -X -P -j $jobId""")
    if (exit != 0) throw IllegalStateException("Slurm job info returned $exit with output: $output")
    val timeSplit = output.trim().split(":")
    if (timeSplit.size != 3) throw IllegalStateException("Bad output: $output")

    val hours = timeSplit[0].toIntOrNull() ?: throw IllegalStateException("Bad hours: $output")
    val minutes = timeSplit[1].toIntOrNull() ?: throw IllegalStateException("Bad minutes: $output")
    val seconds = timeSplit[2].toIntOrNull() ?: throw IllegalStateException("Bad seconds: $output")

    return SimpleDuration(hours, minutes, seconds)
}
