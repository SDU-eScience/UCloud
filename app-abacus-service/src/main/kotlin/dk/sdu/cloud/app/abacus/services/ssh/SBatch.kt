package dk.sdu.cloud.app.abacus.services.ssh

data class SBatchSubmissionResult(val exitCode: Int, val output: String, val jobId: Long?)

private val submitRegex = Regex("Submitted batch job (\\d+)")

fun SSHConnection.sbatch(
    file: String,
    reservation: String? = null,
    vararg args: String
): SBatchSubmissionResult {
    val command = ArrayList<String>().apply {
        add("sbatch")
        if (reservation != null) {
            BashEscaper.safeBashArgument("--reservation=$reservation")
        }
        add(BashEscaper.safeBashArgument(file))
        addAll(args)
    }.joinToString(" ")

    val (exit, output) = exec(command) { inputStream.reader().readText() }

    val match = submitRegex.find(output)
    return if (match != null) {
        SBatchSubmissionResult(exit, output, match.groupValues[1].toLong())
    } else {
        SBatchSubmissionResult(exit, output, null)
    }
}

