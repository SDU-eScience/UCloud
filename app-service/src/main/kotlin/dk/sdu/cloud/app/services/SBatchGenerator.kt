package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ToolBackend
import dk.sdu.cloud.app.util.BashEscaper.safeBashArgument

class SBatchGenerator {
    fun generate(
        description: ApplicationDescription,
        startRequest: AppRequest.Start,
        workDir: String
    ): String {
        val parameters = startRequest.parameters

        // TODO Exception handling
        val tool = ToolDAO.findByNameAndVersion(description.tool.name, description.tool.version)!!

        val resolvedParameters = description.parameters.associate { it to it.map(parameters[it.name]) }
        val givenParameters = resolvedParameters.filterValues { it != null }.mapValues { it.value!! }

        val numberOfNodes = startRequest.numberOfNodes ?: tool.defaultNumberOfNodes
        val tasksPerNode = startRequest.tasksPerNode ?: tool.defaultTasksPerNode
        val maxTime = startRequest.maxTime ?: tool.defaultMaxTime

        val invocation = description.invocation.buildSafeBashString(givenParameters)

        // We should validate at tool level as well, but we do it here as well, just in case
        val requiredModules = ArrayList<String>()
        requiredModules.addAll(tool.requiredModules)

        when (tool.backend) {
            ToolBackend.SINGULARITY -> requiredModules.add("singularity")
            else -> {}
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
                    addAll(listOf("udocker", "run", "--rm"))
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
        val batchJob = """
            #!/bin/bash
            #SBATCH --account sduescience_slim
            #SBATCH --nodes $numberOfNodes
            #SBATCH --ntasks-per-node $tasksPerNode
            #SBATCH --time $maxTime

            $modules

            srun $runCommand $stdRedirect
            """.trimIndent()

        return batchJob
    }
}
