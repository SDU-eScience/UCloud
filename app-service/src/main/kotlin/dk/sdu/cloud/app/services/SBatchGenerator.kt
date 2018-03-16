package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.AppRequest
import dk.sdu.cloud.app.api.ApplicationDescription
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
        val modules = tool.requiredModules.joinToString("\n") {
            "module add ${safeBashArgument(it)}"
        }

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

            module add singularity
            $modules

            srun singularity exec -C -H ${safeBashArgument(workDir)} ${safeBashArgument(tool.container)} $invocation 2> ${safeBashArgument(workDir + "/stderr.txt")} > ${safeBashArgument(workDir + "/stdout.txt")}
            """.trimIndent()

        return batchJob
    }
}
