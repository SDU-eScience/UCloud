package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.file.api.normalize
import dk.sdu.cloud.service.k8.Pod

/**
 * A plugin which takes information from [ApplicationInvocationDescription.parameters] and makes the information
 * available to the user-container
 *
 * Concretely this means that the following changes will be made:
 *
 * - The container will receive a new command
 * - Environment variables will be initialized with values from the user
 */
object ParameterPlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val app = resources.findResources(job).application.invocation
        val givenParameters =
            job.parameters.parameters!!.mapNotNull { (paramName, value) ->
                app.parameters.find { it.name == paramName }!! to value
            }.toMap()

        (builder.spec?.tasks ?: error("no volcano tasks")).forEach { task ->
            val containers = task.template?.spec?.containers ?: error("no containers in task")
            containers.forEach { container ->
                container.command = run {
                    app.invocation.flatMap { parameter ->
                        parameter.buildInvocationList(givenParameters, builder = OurArgBuilder)
                    }
                }

                container.env = run {
                    val envVars = ArrayList<Pod.EnvVar>()
                    app.environment?.forEach { (name, value) ->
                        val resolvedValue = value.buildEnvironmentValue(givenParameters, builder = OurArgBuilder)
                        if (resolvedValue != null) {
                            envVars.add(Pod.EnvVar(name, resolvedValue, null))
                        }
                    }

                    envVars
                }
            }
        }
    }
}

private object OurArgBuilder : ArgumentBuilder {
    override fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
        return when (parameter) {
            is ApplicationParameter.InputFile -> {
                val file = (value as AppParameterValue.File)
                val components = file.path.normalize().components()
                if (components.size < 2) {
                    return ArgumentBuilder.Default.build(parameter, value)
                }

                joinPath("/work", components[components.lastIndex - 1], components[components.lastIndex])
            }

            is ApplicationParameter.InputDirectory -> {
                val file = (value as AppParameterValue.File)
                val components = file.path.normalize().components()
                if (components.isEmpty()) {
                    return ArgumentBuilder.Default.build(parameter, value)
                }
                joinPath("/work", components[components.lastIndex])
            }


            else -> ArgumentBuilder.Default.build(parameter, value)
        }
    }
}