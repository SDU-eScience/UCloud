package dk.sdu.cloud.app.abacus.services

import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.api.VerifiedJob
import dk.sdu.cloud.app.api.buildSafeBashString
import dk.sdu.cloud.service.BashEscaper.safeBashArgument

class SBatchGenerator {
    fun generate(
        verifiedJob: VerifiedJob,
        workDir: String
    ): String {
        val app = verifiedJob.application.description
        val tool = verifiedJob.application.tool.description
        val givenParameters = verifiedJob.jobInput.asMap().mapNotNull { (paramName, value) ->
            if (value != null) {
                app.parameters.find { it.name == paramName }!! to value
            } else {
                null
            }
        }.toMap()

        val invocation = app.invocation.buildSafeBashString(givenParameters)

        // We should validate at tool level as well, but we do it here as well, just in case
        val requiredModules = ArrayList<String>()
        requiredModules.addAll(tool.requiredModules)

        when (tool.backend) {
            ToolBackend.SINGULARITY -> requiredModules.add("singularity")
            else -> {
                // No extra required modules
            }
        }

        val modules = requiredModules.joinToString("\n") {
            "module add ${safeBashArgument(it)}"
        }

        val runCommand = when (tool.backend) {
            ToolBackend.SINGULARITY -> {
                ArrayList<String>().apply {
                    addAll(listOf("singularity", "exec", "-C"))

                    addAll(listOf("-H", safeBashArgument(workDir)))
                    add(safeBashArgument(tool.container))
                    add(invocation)
                }.joinToString(" ")
            }

            ToolBackend.UDOCKER -> {
                ArrayList<String>().apply {
                    val containerWorkDir = "/scratch"
                    addAll(listOf("/home/sducloudapps/bin/udocker-prep", "-q", "run", "--rm"))
                    add("--workdir=$containerWorkDir")
                    add("--volume=${safeBashArgument(workDir)}:$containerWorkDir")
                    add(safeBashArgument(tool.container))
                    add(invocation)
                }.joinToString(" ")
            }
        }

        val stdRedirect = "2> ${safeBashArgument("$workDir/stderr.txt")} > " +
                safeBashArgument("$workDir/stdout.txt")

        //
        //
        //
        // NOTE: ALL USER INPUT _MUST_ GO THROUGH SANITIZATION (use safeBashArgument). OTHERWISE USERS WILL BE ABLE
        // TO RUN CODE AS THE ESCIENCE ACCOUNT (AND ACCESS FILES FROM OTHER PROJECTS!)
        //
        //
        //

        return """
            #!/bin/bash
            #SBATCH --account sduescience_slim
            #SBATCH --nodes ${verifiedJob.nodes}
            #SBATCH --ntasks-per-node ${verifiedJob.tasksPerNode}
            #SBATCH --time ${verifiedJob.maxTime}

            $modules

            srun $runCommand $stdRedirect
            """.trimIndent()
    }
}
