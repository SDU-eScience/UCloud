package org.esciencecloud.abc.ssh

data class SBatchSubmissionResult(val exitCode: Int, val output: String, val jobId: Long?)

private val SBATCH_SUBMIT_REGEX = Regex("Submitted batch job (\\d+)")

fun SSHConnection.sbatch(file: String, vararg args: String): SBatchSubmissionResult {
    val (exit, output) = exec("sbatch $file ${args.joinToString(" ")}") { inputStream.reader().readText() }

    val match = SBATCH_SUBMIT_REGEX.find(output)
    return if (match != null) {
        SBatchSubmissionResult(exit, output, match.groupValues[1].toLong())
    } else {
        SBatchSubmissionResult(exit, output, null)
    }
}

