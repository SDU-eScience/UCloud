package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.ArgumentBuilder
import dk.sdu.cloud.config.*
import dk.sdu.cloud.file.orchestrator.api.components
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.plugins.storage.UCloudFile

private fun escapeBash(value: String): String {
    return buildString {
        for (char in value) {
            append(
                when (char) {
                    '\\' -> "\\\\"
                    '\'' -> "'\"'\"'"
                    '\"' -> "\\\""
                    '`' -> "\\`"
                    '$' -> "\\$"
                    else -> char
                }
            )
        }
    }
}

suspend fun createSbatchFile(ctx: PluginContext, job: Job, config: SlurmConfig): String {
    @Suppress("DEPRECATION") val timeAllocation = job.specification.timeAllocation
        ?: job.status.resolvedApplication!!.invocation.tool.tool!!.description.defaultTimeAllocation

    val formattedTime = "${timeAllocation.hours}:${timeAllocation.minutes}:${timeAllocation.seconds}"
    val resolvedProduct = job.status.resolvedProduct!!

    //sbatch will stop processing further #SBATCH directives once the first non-comment non-whitespace line has been reached in the script.
    // remove whitespaces
    val app = job.status.resolvedApplication!!.invocation
    val givenParameters =
        job.specification.parameters!!.mapNotNull { (paramName, value) ->
            app.parameters.find { it.name == paramName }!! to value
        }.toMap()
    val cliInvocation = app.invocation.flatMap { parameter ->
        parameter.buildInvocationList(givenParameters, builder = OurArgBuilder(PathConverter(ctx)))
    }.joinToString(separator = " ") { "'" + escapeBash(it) + "'" }

    val memoryAllocation = if (config.useFakeMemoryAllocations) {
        "50M"
    } else {
        "${resolvedProduct.memoryInGigs ?: 1}G"
    }

    /*
     *   https://slurm.schedmd.com/sbatch.html
     *   %n - Node identifier relative to current job (e.g. "0" is the first node of the running job) This will create a separate IO file per node.
     */

    return buildString {
        appendLine("#!/usr/bin/env bash")
        appendLine("#")
        appendLine("# POSTFIX START")
        appendLine("#")
        appendLine("#SBATCH --chdir ${config.mountpoint}/${job.id}")
        appendLine("#SBATCH --cpus-per-task ${resolvedProduct.cpu ?: 1}")
        appendLine("#SBATCH --mem $memoryAllocation")
        appendLine("#SBATCH --gpus-per-node ${resolvedProduct.gpu ?: 0}")
        appendLine("#SBATCH --time $formattedTime")
        appendLine("#SBATCH --nodes ${job.specification.replicas}")
        appendLine("#SBATCH --job-name ${job.id}")
        appendLine("#SBATCH --partition ${config.partition}")
        appendLine("#SBATCH --parsable")
        appendLine("#SBATCH --output=std.out")
        appendLine("#SBATCH --error=std.err")
        appendLine("#SBATCH --get-user-env")
        appendLine("#")
        appendLine("# POSTFIX END")
        appendLine("#")
        appendLine("srun --output='std-%n.out' --error='std-%n.err' " + cliInvocation)
        appendLine("#EOF")
    }
}

private class OurArgBuilder(
    private val pathConverter: PathConverter,
) : ArgumentBuilder {
    override suspend fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
        return when (parameter) {
            is ApplicationParameter.InputFile, is ApplicationParameter.InputDirectory -> {
                val file = (value as AppParameterValue.File)
                pathConverter.ucloudToInternal(UCloudFile.create(file.path)).path
            }

            else -> ArgumentBuilder.Default.build(parameter, value)
        }
    }
}
