package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.ApplicationDescription
import dk.sdu.cloud.app.api.ApplicationParameter
import dk.sdu.cloud.app.api.SimpleDuration
import dk.sdu.cloud.app.util.BashEscaper.safeBashArgument

class SBatchGenerator {
    fun generate(
        description: ApplicationDescription,
        parameters: Map<String, Any>,
        workDir: String
    ): String {
        // TODO Exception handling
        val tool = ToolDAO.findByNameAndVersion(description.tool.name, description.tool.version)!!

        val resolvedParameters = description.parameters.associate { it to it.map(parameters[it.name]) }

        val numberOfNodes = tool.defaultNumberOfNodes
        val tasksPerNode = tool.defaultTasksPerNode
        val maxTime = tool.defaultMaxTime

        val invocation = description.invocation.buildSafeBashString(resolvedParameters)

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

            srun singularity run -C -H ${safeBashArgument(workDir)} ${safeBashArgument(tool.container)} $invocation
            """.trimIndent()

        return batchJob
    }
}
