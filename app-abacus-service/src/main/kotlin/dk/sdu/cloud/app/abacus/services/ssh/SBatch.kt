package dk.sdu.cloud.app.abacus.services.ssh

data class SBatchSubmissionResult(val exitCode: Int, val output: String, val jobId: Long?)

private val submitRegex = Regex("Submitted batch job (\\d+)")

fun SSHConnection.sbatch(file: String, vararg args: String): SBatchSubmissionResult {
    val (exit, output) = exec("sbatch $file ${args.joinToString(" ")}") { inputStream.reader().readText() }

    val match = submitRegex.find(output)
    return if (match != null) {
        SBatchSubmissionResult(exit, output, match.groupValues[1].toLong())
    } else {
        SBatchSubmissionResult(exit, output, null)
    }
}

