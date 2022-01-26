package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.utils.executeCommandToText

object SlurmCommandLine {
    const val SBATCH_EXE = "/usr/bin/sbatch"
    const val SCANCEL_EXE = "/usr/bin/scancel"
    const val SQUEUE_EXE = "/usr/bin/squeue"
    const val SINFO_EXE = "/usr/bin/sinfo"
    const val SACCT_EXE = "/usr/bin/sacct"
    const val SLURM_CONF_KEY = "SLURM_CONF"
    const val SLURM_CONF_VALUE = "/etc/slurm/slurm.conf"
    const val SSH_EXE = "/usr/bin/ssh"
    const val STTY_EXE = "/usr/bin/stty"
    const val PS_EXE = "/usr/bin/ps"

    fun submitBatchJob(pathToFile: String): String {
        val (code, stdout, stderr) = executeCommandToText(SBATCH_EXE) {
            addArg(pathToFile)
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

        if (code != 0) {
            throw RPCException(
                "Unhandled exception when creating job: $code $stdout $stderr",
                HttpStatusCode.BadRequest
            )
        }

        return stdout.trim()
    }

    fun cancelJob(partition: String, jobId: String) {
        val resp = executeCommandToText(SCANCEL_EXE) {
            addArg("--partition", partition)
            addArg("--full")
            addArg(jobId)
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
        }

        if (resp.statusCode != 0) {
            throw RPCException(
                "Failed to cancel job (${resp.stdout} ${resp.statusCode})",
                HttpStatusCode.InternalServerError
            )
        }
    }

    fun browseJobAllocations(
        slurmIds: List<String>,
        partition: String? = null,
    ): List<SlurmAllocation> {
        val (_, stdout, _) = executeCommandToText(SACCT_EXE) {
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)

            addArg("--jobs", slurmIds.joinToString(","))
            addArg("--format", "jobid,state,exitcode,start,end")
            addArg("--parsable2")

            if (partition != null) addArg("--partitions", partition)

            // Displays all users' jobs when run by user root or if PrivateData is not configured to jobs.
            // Otherwise, display the current user's jobs
            addArg("--allusers")

            // Only show statistics relevant to the job allocation itself, not taking steps into consideration.
            addArg("--allocations")
        }

        val acctStdLines = stdout.lines().map { line -> line.split("|") }
        // create map then list of objects
        val header = acctStdLines[0]
        return acctStdLines
            .mapIndexedNotNull { idx, line ->
                // [{Start=2021-11-03T12:16:36, State=TIMEOUT, ExitCode=0:0, End=2021-11-03T12:21:46, JobID=33}, {Start=2021-11-03T12:16:36, State=TIMEOUT, ExitCode=0:0, End=2021-11-03T12:21:46, JobID=34}]
                val map: MutableMap<String, String> = HashMap()
                line.forEachIndexed { idx, word ->
                    map[header[idx]] = word
                }
                if (idx > 0) map else null
            }
            .map { map ->
                // [acctEntry(jobId=33, state=TIMEOUT, exitCode=0:0, start=2021-11-03T12:16:36, end=2021-11-03T12:21:46), acctEntry(jobId=34, state=TIMEOUT, exitCode=0:0, start=2021-11-03T12:16:36, end=2021-11-03T12:21:46)]
                SlurmAllocation(
                    map["JobID"] ?: error("JobID not returned from slurm output: $map"),
                    (map["State"]?.takeIf { it.isNotEmpty() }
                        ?: error("State not returned from slurm output: $map")).split(" ")[0],
                    map["ExitCode"] ?: error("Exit code not returned from slurm output: $map"),
                    map["Start"] ?: error("Start not returned from slurm output: $map"),
                    map["End"] ?: error("End not returned from slurm output: $map")
                )
            }
    }
}