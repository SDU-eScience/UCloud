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
    	
        //match pattern c[001,3-5]
        //val regexCompressed = """([a-zA-Z]+)\[([0-9\,\-]+)\]""".toRegex()

        val regexCompressed = """([a-z]+)           # group that matches set of at least one letter ex: nodename
                                 \[                 # matches opening bracket ex: [
                                 ([\d\,\-]+)        # group that matches set of digit, comma, minus, at least one occurence ex: 003,009-015
                                 \]                 # matches closing bracket ex: ]
                              """.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))
        

        //matches pattern c2
        val regexSimple = """[a-z]+     # matches a set of at least one letter ex: nodename
                             \d+        # matches at least one digit ex: 001
                          """.toRegex(setOf(RegexOption.COMMENTS, RegexOption.IGNORE_CASE))
        
        var matchResult = regexCompressed.findAll(str).iterator()
        
        if(!matchResult.hasNext()){
            val simpleResult = regexSimple.find(str)
            //c2
            nodes.add(simpleResult!!.value)
        } 
        
            //c[1,3-5]
    	while( matchResult.hasNext() ) {

            val (nodeName, nodeNumbers) = matchResult.next()!!.destructured

            nodeNumbers.split(",").forEach{ seq -> 
                if(seq.contains("-")) {
                    val min = seq.split("-")[0].toInt()
                    val max = seq.split("-")[1].toInt()
                    for (i in min..max) nodes.add("${nodeName}${i}")
                } else {
                    nodes.add("${nodeName}${seq.toInt()}")
                }
            }
              
        } 

        if(nodes.isEmpty()) throw Exception("Empty NodeList")
        return nodes.mapIndexedNotNull{idx, item -> idx to item}.toMap()
    }


}