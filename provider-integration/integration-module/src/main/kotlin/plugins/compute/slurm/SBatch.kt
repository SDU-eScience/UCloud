package dk.sdu.cloud.plugins.compute.slurm

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.app.orchestrator.api.*
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.ApplicationType
import dk.sdu.cloud.app.store.api.ArgumentBuilder
import dk.sdu.cloud.app.store.api.BooleanFlagParameter
import dk.sdu.cloud.app.store.api.EnvironmentVariableParameter
import dk.sdu.cloud.app.store.api.InvocationParameter
import dk.sdu.cloud.app.store.api.InvocationParameterContext
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.api.VariableInvocationParameter
import dk.sdu.cloud.app.store.api.WordInvocationParameter
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.*
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.storage.PathConverter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.random.Random

private fun escapeBash(value: String): String {
    return buildString {
        for (char in value) {
            append(
                when (char) {
                    '\'' -> "'\"'\"'"
                    else -> char
                }
            )
        }
    }
}

suspend fun createSbatchFile(
    ctx: PluginContext,
    job: Job,
    plugin: SlurmPlugin,
    account: String?,
    jobFolder: String?,
): String {
    val pluginConfig = plugin.pluginConfig

    @Suppress("DEPRECATION") val timeAllocation = job.specification.timeAllocation
        ?: job.status.resolvedApplication!!.invocation.tool.tool!!.description.defaultTimeAllocation

    val formattedTime = "${timeAllocation.hours}:${timeAllocation.minutes}:${timeAllocation.seconds}"
    val resolvedProduct = job.status.resolvedProduct!!

    //sbatch will stop processing further #SBATCH directives once the first non-comment non-whitespace line has been reached in the script.
    // remove whitespaces
    val app = job.status.resolvedApplication!!.invocation
    val tool = job.status.resolvedApplication!!.invocation.tool.tool!!

    val defaultParameters: Map<ApplicationParameter, AppParameterValue> = buildMap {
        for (it in app.parameters) {
            if (it.defaultValue == null) continue
            // NOTE: We might have old data which is not AppParameterValues, those we ignore and just continue
            // without a default value
            val value = runCatching {
                when (it) {
                    is ApplicationParameter.InputDirectory,
                    is ApplicationParameter.InputFile ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.File>(it.defaultValue!!)

                    is ApplicationParameter.Bool ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Bool>(it.defaultValue!!)

                    is ApplicationParameter.FloatingPoint ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.FloatingPoint>(it.defaultValue!!)

                    is ApplicationParameter.Ingress ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Ingress>(it.defaultValue!!)

                    is ApplicationParameter.Integer ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Integer>(it.defaultValue!!)

                    is ApplicationParameter.LicenseServer ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.License>(it.defaultValue!!)

                    is ApplicationParameter.NetworkIP ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Network>(it.defaultValue!!)

                    is ApplicationParameter.Peer ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Peer>(it.defaultValue!!)

                    is ApplicationParameter.Text,
                    is ApplicationParameter.Enumeration ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.Text>(it.defaultValue!!)

                    is ApplicationParameter.TextArea ->
                        defaultMapper.decodeFromJsonElement<AppParameterValue.TextArea>(it.defaultValue!!)
                }
            }.getOrNull() ?: continue

            put(it, value)
        }
    }

    val givenParameters =
        job.specification.parameters!!.mapNotNull { (paramName, value) ->
            app.parameters.find { it.name == paramName }!! to value
        }.toMap()

    val allParameters = defaultParameters + givenParameters

    val argBuilder = OurArgBuilder(PathConverter(ctx))
    var cliInvocation = app.invocation.flatMap { parameter ->
        when (parameter) {
            is EnvironmentVariableParameter -> {
                listOf("$" + parameter.variable)
            }
            else -> {
                parameter.buildInvocationList(allParameters, builder = argBuilder).map { "'" + escapeBash(it) + "'" }
            }
        }

    }.joinToString(separator = " ")

    val memoryAllocation = if (pluginConfig.useFakeMemoryAllocations) {
        "50"
    } else {
        "${(resolvedProduct.memoryInGigs ?: 1) * 1000}"
    }

    val cpuAllocation = if (pluginConfig.useFakeMemoryAllocations) {
        1
    } else {
        resolvedProduct.cpu ?: 1
    }

    /*
     *   https://slurm.schedmd.com/sbatch.html
     *   %n - Node identifier relative to current job (e.g. "0" is the first node of the running job) This will create
     *        a separate IO file per node.
     */

    return buildString {
        appendLine("#!/usr/bin/env bash")

        val parameters = job.specification.parameters ?: emptyMap()
        val nTasksPerNode = (parameters["nTasksPerNode"] as? AppParameterValue.Integer)?.value ?: 1
        val cpusPerNode = resolvedProduct.cpu ?: 1
        val cpusPerTask = cpusPerNode / nTasksPerNode

        if (nTasksPerNode <= 0) {
            throw RPCException(
                "You cannot supply a negative number of tasks per node. Please provide a positive value of at least 1.",
                HttpStatusCode.BadRequest
            )
        }

        if (cpusPerTask <= 0) {
            throw RPCException(
                "You have granted too many CPUs per task. You only have $cpusPerNode available for each node and " +
                        "your allocation would have required at least $nTasksPerNode CPUs.",
                HttpStatusCode.BadRequest
            )
        }

        run {
            if (jobFolder != null) appendLine("#SBATCH --chdir \"$jobFolder\"")
            appendLine("#SBATCH --cpus-per-task $cpuAllocation")
            appendLine("#SBATCH --ntasks-per-node $nTasksPerNode")
            appendLine("#SBATCH --mem $memoryAllocation")
            appendLine("#SBATCH --gpus-per-node ${resolvedProduct.gpu ?: 0}")
            appendLine("#SBATCH --time $formattedTime")
            appendLine("#SBATCH --nodes ${job.specification.replicas}")
            appendLine("#SBATCH --job-name ${job.id}")
            appendLine("#SBATCH --partition ${pluginConfig.partition}")
            appendLine("#SBATCH --parsable")
            appendLine("#SBATCH --output=stdout.txt")
            appendLine("#SBATCH --error=stderr.txt")
            // TODO(Dan): This definitely doesn't do anything meaningful on a real system, and I am not sure it
            //  does anything on any system. It sounds like from the documentation that this only works if sbatch is
            //  executed as root and run with --uid.
            appendLine("#SBATCH --get-user-env")
            if (account != null) appendLine("#SBATCH --account=$account")

            for ((index, constraintMatcher) in pluginConfig.constraints.withIndex()) {
                val productMatcher = plugin.constraintMatchers[index]
                val matches = productMatcher.match(job.specification.product.removeProvider()) >= 0
                if (matches) {
                    appendLine("#SBATCH --constraint=${constraintMatcher.constraint}")
                    break
                }
            }
        }

        val appMetadata = job.status.resolvedApplication!!.metadata
        if (appMetadata.name.startsWith(slurmRawScriptPrefix) || appMetadata.name == "slurm-script") {
            val script = (parameters["script"] as? AppParameterValue.Text)?.value
                ?: "echo 'No script found'"
            appendLine(script)
            return@buildString
        }

        val allocatedPort = Random.nextInt(10_000, 50_000)

        if (app.applicationType == ApplicationType.WEB) {
            appendLine("export UCLOUD_PORT=$allocatedPort")
            appendLine("echo $allocatedPort > '$jobFolder/${SlurmPlugin.ALLOCATED_PORT_FILE}'")
        }

        for ((key, param) in (app.environment ?: emptyMap())) {
            val value = param.buildInvocationList(allParameters, InvocationParameterContext.ENVIRONMENT, argBuilder)
                .joinToString(separator = " ") { "'" + escapeBash(it) + "'" }

            appendLine("export $key=$value")
        }

        if (tool.description.requiredModules.isNotEmpty()) {
            appendLine("module purge")
            for (module in tool.description.requiredModules) {
                appendLine("module load '${module}'")
            }
        }

        val udocker = plugin.udocker
        val useUDocker = tool.description.backend == ToolBackend.DOCKER && udocker != null
        if (useUDocker && udocker != null) {
            val containerImage = (tool.description.container ?: tool.description.image)!!
            val udockerPath = udocker.install()
            ProcessingScope.launch {
                udocker.pullImage(containerImage)
            }

            // TODO(Dan): Need to verify that the image has been downloaded before the script continues.

            appendLine("export PATH=\$PATH:$udockerPath")

            // TODO(Dan): Only the bash part has been tested, haven't tested if this works in practice.
            appendLine(
                """
                    while :
                    do
                        udocker images | grep "$containerImage" 2> /dev/null
                        if [ "$?" -eq 0 ]
                        then
                            break
                        fi

                        sleep 1
                    done    
                """.trimIndent()
            )

            appendLine("udocker create --name=${job.id} $containerImage")
            appendLine("udocker setup --execmode=${pluginConfig.udocker.execMode.name} ${job.id}")
            val oldCli = cliInvocation
            cliInvocation = "udocker run "
            cliInvocation += "--bindhome "
            cliInvocation += "--hostauth "
            cliInvocation += "--hostenv "
            cliInvocation += "-v /sys "
            cliInvocation += "-v /proc "
            cliInvocation += "-v /var/run  "
            cliInvocation += "-v /dev "
            cliInvocation += "--nobanner "
            cliInvocation += "--dri "
            cliInvocation += "--publish $allocatedPort:$allocatedPort "
            cliInvocation += "${job.id} "
            cliInvocation += oldCli
        }

        // appendLine("srun --output='std-%n.out' --error='std-%n.err' $cliInvocation")
        appendLine(cliInvocation)
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

private const val slurmRawScriptPrefix = "slurm-script-"
