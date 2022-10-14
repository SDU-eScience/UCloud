package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.utils.CommandBuilder
import dk.sdu.cloud.utils.DateDetails
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.gmtTime

class SlurmCommandLine(
    private val modifySlurmConf: String? = "/etc/slurm/slurm.conf",
) {
    private fun CommandBuilder.configureSlurm() {
        if (modifySlurmConf != null) addEnv(SLURM_CONF_KEY, modifySlurmConf)
    }

    private val defaultPath = System.getenv("PATH")

    suspend fun submitBatchJob(pathToFile: String): String {
        val (code, stdout, stderr) = executeCommandToText(SBATCH_EXE) {
            addArg(pathToFile)
            configureSlurm()

            // HACK(Dan): --get-user-env doesn't really do a lot for us. We need to submit the job with something
            // that actually resembles the user's login shell and not whatever this hack is doing.
            if (defaultPath != null) addEnv("PATH", defaultPath)
        }

        if (code != 0) {
            throw RPCException(
                "Unhandled exception when creating job: $code $stdout $stderr",
                HttpStatusCode.BadRequest
            )
        }

        return stdout.trim()
    }

    suspend fun cancelJob(partition: String, jobId: String) {
        val resp = executeCommandToText(SCANCEL_EXE) {
            addArg("--partition", partition)
            addArg("--full")
            addArg(jobId)
            configureSlurm()
        }

        if (resp.statusCode != 0) {
            throw RPCException(
                "Failed to cancel job. You can only cancel a job which you have started.",
                HttpStatusCode.BadRequest
            )
        }
    }

    suspend fun browseJobAllocations(
        slurmIds: List<String>,
        partition: String? = null,
    ): List<SlurmAllocation> {
        val (_, stdout, _) = executeCommandToText(SACCT_EXE) {
            configureSlurm()

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

    suspend fun getJobNodeList(slurmId: String): Map<Int, String> {
        val (_, stdout, _) = executeCommandToText(SACCT_EXE) {
            configureSlurm()
            addArg("--jobs", slurmId)
            addArg("--format", "nodelist")
            addArg("--parsable2")
            addArg("--allusers")
            addArg("--allocations")
            addArg("--noheader")
        }

        return expandNodeList(stdout)
    }

    private suspend fun expandNodeList(str: String): Map<Int, String> {
        if (str.isBlank()) return emptyMap()

        val (code, stdout) = executeCommandToText(SCTL_EXE) {
            configureSlurm()
            addArg("show")
            addArg("hostname")
            addArg(str)
        }

        if (code != 0) error("Unknown exception when performing expandNodeList")

        val nodes = stdout.lines().filter { it.isNotBlank() }
        return nodes.mapIndexed { idx, item -> idx to item }.toMap()
    }

    data class SlurmLogFiles(val stdout: String?, val stderr: String?)
    suspend fun readLogFileLocation(jobId: String): SlurmLogFiles {
        val (code, stdout) = executeCommandToText(SCTL_EXE) {
            configureSlurm()
            addArg("show", "job")
            addArg(jobId)
        }

        val fields = stdout.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("StdOut=", ignoreCase = true) || it.startsWith("StdErr=", ignoreCase = true) }
            .map { line ->
                val key = line.substringBefore('=').lowercase()
                val value = line.substringAfter('=').takeIf { it.isNotBlank() }
                key to value
            }
            .toMap()

        return SlurmLogFiles(fields["stdout"], fields["stderr"])
    }

    data class SlurmAccountingRow(
        val jobId: Long,
        val timeElappsedMs: Long,
        val memoryRequestedInMegs: Long,
        val cpusRequested: Int,
        val uid: Int,
        val slurmAccount: String?,
        val nodesRequested: Int,
        val jobName: String,
        val gpu: Int,
        val timeAllocationMillis: Long,
        val state: UcloudStateInfo,
    )

    suspend fun retrieveAccountingData(
        since: Long,
        partition: String,
    ): List<SlurmAccountingRow> {
        val rows = executeCommandToText(SACCT_EXE) {
            configureSlurm()

            addArg("-a")
            if (since != 0L) addArg("-S", gmtTime(since).formatForSlurm())
            addArg("-oJobID,Elapsed,ReqMem,ReqCPUS,Uid,State,Account,AllocNodes,JobName,Timelimit")
            addArg("-r", partition)
            addArg("-X")
            addArg("--parsable2")
        }.stdout.lines().drop(1)

        return rows.mapNotNull { row ->
            val columns = row.split("|")
            if (columns.size != 10) return@mapNotNull null

            val cpusRequested = columns[3].toIntOrNull() ?: return@mapNotNull null
            val nodesRequested = columns[7].toIntOrNull() ?: return@mapNotNull null

            val jobId = columns[0].toLongOrNull() ?: return@mapNotNull null
            val timeElappsedMs = slurmDurationToMillis(columns[1]) ?: return@mapNotNull null
            val memoryRequestedInMegs = columns[2].let { formatted ->
                val textValue = formatted.replace(memorySuffix, "")
                val value = textValue.toDoubleOrNull() ?: return@mapNotNull null
                val suffix = formatted.removePrefix(textValue)
                if (suffix.isEmpty()) return@mapNotNull null

                val unit = suffix[0]
                val type = suffix.getOrNull(1)

                val multiplierA = when (unit) {
                    'K' -> 1_000L
                    'M' -> 1_000_000L
                    'G' -> 1_000_000_000L
                    'T' -> 1_000_000_000_000L
                    'P' -> 1_000_000_000_000_000L
                    else -> return@mapNotNull null
                }

                val multiplierB = when (type) {
                    'c' -> cpusRequested
                    'n' -> nodesRequested
                    else -> 1
                }

                val ramInBytes = value * multiplierA * multiplierB
                (ramInBytes / 1_000_000L).toLong()
            }
            val uid = columns[4].toIntOrNull() ?: return@mapNotNull null
            val slurmAccount = columns[6].takeIf { it.isNotBlank() }
            val jobName = columns[8]
            val timeAllocationMillis = slurmDurationToMillis(columns[9]) ?: return@mapNotNull null
            val state = SlurmStateToUCloudState.slurmToUCloud[columns[5].substringBefore(' ')] ?: return@mapNotNull null

            SlurmAccountingRow(
                jobId,
                timeElappsedMs,
                memoryRequestedInMegs,
                cpusRequested,
                uid,
                slurmAccount,
                nodesRequested,
                jobName,
                0, // TODO(Dan): Parse the number of GPUs
                timeAllocationMillis,
                state,
            )
        }
    }

    private fun DateDetails.formatForSlurm(): String = buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append((month.ordinal + 1).toString().padStart(2, '0'))
        append('-')
        append(dayOfMonth.toString().padStart(2, '0'))
        append('T')
        append(hours.toString().padStart(2, '0'))
        append(':')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
    }

    private fun slurmDurationToMillis(duration: String): Long? {
        val components = duration.split(":")
        if (components.size != 3) return null
        val hours = components[0].removePrefix("0").toIntOrNull() ?: return null
        val minutes = components[1].removePrefix("0").toIntOrNull() ?: return null
        val seconds = components[2].removePrefix("0").toIntOrNull() ?: return null

        return (seconds * 1000L) + (minutes * 1000L * 60) + (hours * 1000L * 60 * 60)
    }

    companion object {
        const val SBATCH_EXE = "/usr/bin/sbatch"
        const val SCANCEL_EXE = "/usr/bin/scancel"
        const val SQUEUE_EXE = "/usr/bin/squeue"
        const val SINFO_EXE = "/usr/bin/sinfo"
        const val SACCT_EXE = "/usr/bin/sacct"
        const val SRUN_EXE = "/usr/bin/srun"
        const val SCTL_EXE = "/usr/bin/scontrol"
        const val SLURM_CONF_KEY = "SLURM_CONF"
        private val memorySuffix = Regex("[KMGTP][cn]?")
    }
}
