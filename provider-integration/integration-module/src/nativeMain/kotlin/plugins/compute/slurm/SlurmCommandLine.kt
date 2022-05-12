package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.utils.DateDetails
import dk.sdu.cloud.utils.executeCommandToText
import dk.sdu.cloud.utils.gmtTime

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


    //Various cases to cover slurm compressed node format
    //adev[1-2]
    //adev[6,13,15]  
    //adev[7-8,14]
    //adev7
    // or any combination thereof

    fun getJobNodeList(slurmId: String):Map<Int,String>{

        val (_, stdout, _) = executeCommandToText(SACCT_EXE) {
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)
            addArg("--jobs", slurmId)
            addArg("--format", "nodelist")
            addArg("--parsable2")
            addArg("--allusers")
            addArg("--allocations")
        }

        return expandNodeList(stdout)

    }



   fun expandNodeList(str: String):Map<Int,String> {

            var nodes:MutableList<String> = mutableListOf()
            
            //match single node c2, c3
            val regexSingleNode = """[a-z]+\d+""".toRegex(setOf(RegexOption.IGNORE_CASE))

            //match pattern c[001,3-5,0010]
            val regexCompressed = """([a-z]+)           # group that matches set of at least one letter ex: nodename
                                     \[                 # matches opening bracket ex: [
                                     ([\d\,\-]+)        # group that matches set of digit, comma, minus, at least one occurence ex: 003,009-015
                                     \]                 # matches closing bracket ex: ]
                                  """.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))
            
            //match pattern c[001,3-5,0010]
            val regexAny = """${regexCompressed}             # adev[1-5,8,9] c[1-2]
                              |  							 # OR
                              ${regexSingleNode}			 # c2 c6 c7
                           """.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))
            
            
            var iterator = regexAny.findAll(str).iterator()
            
    
            //c[1,3-5]
            while( iterator.hasNext() ) {
                
                val match = iterator.next()
                val matchValue = match.value
                val (nodeName, nodeNumbers) = match.destructured
                
                if( matchValue.matches(regexSingleNode) ) {
                    
                    nodes.add(matchValue)
                    
                } else if ( matchValue.matches(regexCompressed) )  {

                    nodeNumbers.split(",").forEach{ seq -> 
                        if(seq.contains("-")) {
                            val min = seq.split("-")[0].toInt()
                            val max = seq.split("-")[1].toInt()
                            for (i in min..max) nodes.add("${nodeName}${i}")
                        } else {
                            nodes.add("${nodeName}${seq.toInt()}")
                        }
                    }
                    
                } else throw Exception("Unhandled pattern")

            } 

            if(nodes.isEmpty()) throw Exception("Empty NodeList")
            return nodes.mapIndexedNotNull{idx, item -> idx to item}.toMap()

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
    )

    fun retrieveAccountingData(
        since: Long,
        partition: String,
    ): List<SlurmAccountingRow> {
        val rows = executeCommandToText(SACCT_EXE) {
            addEnv(SLURM_CONF_KEY, SLURM_CONF_VALUE)

            addArg("-a")
            addArg("-S", gmtTime(since).formatForSlurm())
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

            SlurmAccountingRow(
                columns[0].toLongOrNull() ?: return@mapNotNull null,
                slurmDurationToMillis(columns[1]) ?: return@mapNotNull null,
                columns[2].let { formatted ->
                    val textValue = formatted.replace(memorySuffix, "")
                    val value = textValue.toLongOrNull() ?: return@mapNotNull null
                    val suffix = formatted.removePrefix(textValue)
                    if (suffix.length != 2) return@mapNotNull null

                    val unit = suffix[0]
                    val type = suffix[1]

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
                        else -> return@mapNotNull null
                    }

                    val ramInBytes = value * multiplierA * multiplierB
                    ramInBytes / 1_000_000L
                },
                cpusRequested,
                columns[4].toIntOrNull() ?: return@mapNotNull null,
                columns[6].takeIf { it.isNotBlank() },
                nodesRequested,
                columns[8],
                0, // TODO(Dan): Parse the number of GPUs
                slurmDurationToMillis(columns[9]) ?: return@mapNotNull null,
            )
        }
    }

    private fun DateDetails.formatForSlurm(): String = buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(month.numericValue.toString().padStart(2, '0'))
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

    private val memorySuffix = Regex("[KMGTP][cn]")
}
