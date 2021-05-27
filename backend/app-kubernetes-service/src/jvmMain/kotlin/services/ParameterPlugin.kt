package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.*
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.k8.Pod
import io.ktor.http.*

/**
 * A plugin which takes information from [ApplicationInvocationDescription.parameters] and makes the information
 * available to the user-container
 *
 * Concretely this means that the following changes will be made:
 *
 * - The container will receive a new command
 * - Environment variables will be initialized with values from the user
 */
class ParameterPlugin(private val licenseService: LicenseService) : JobManagementPlugin {
    private val argBuilder = OurArgBuilder(licenseService)

    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val app = resources.findResources(job).application.invocation
        val givenParameters =
            job.specification.parameters!!.mapNotNull { (paramName, value) ->
                app.parameters.find { it.name == paramName }!! to value
            }.toMap()

        (builder.spec?.tasks ?: error("no volcano tasks")).forEach { task ->
            val containers = task.template?.spec?.containers ?: error("no containers in task")
            containers.forEach { container ->
                container.command = run {
                    app.invocation.flatMap { parameter ->
                        parameter.buildInvocationList(givenParameters, builder = argBuilder)
                    }
                }

                container.env = run {
                    val envVars = ArrayList<Pod.EnvVar>()
                    app.environment?.forEach { (name, value) ->
                        val resolvedValue = value.buildEnvironmentValue(givenParameters, builder = argBuilder)
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

private class OurArgBuilder(private val licenseService: LicenseService) : ArgumentBuilder {
    override suspend fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
        return when (parameter) {
            /*
            is ApplicationParameter.InputFile, is ApplicationParameter.InputDirectory -> {
                val file = (value as AppParameterValue.File)
                val components = file.path.normalize().components()
                if (components.isEmpty()) {
                    return ArgumentBuilder.Default.build(parameter, value)
                }
                joinPath("/work", components[components.lastIndex])
            }

             */

            is ApplicationParameter.LicenseServer -> {
                val retrievedLicense =
                    licenseService.retrieveServerFromInstance((value as AppParameterValue.License).id)
                        ?: throw RPCException(
                            "Invalid license passed for parameter: ${parameter.name}",
                            HttpStatusCode.BadRequest
                        )

                buildString {
                    append(retrievedLicense.address)
                    append(":")
                    append(retrievedLicense.port)
                    if (retrievedLicense.license != null) {
                        append("/")
                        append(retrievedLicense.license)
                    }
                }
            }

            else -> ArgumentBuilder.Default.build(parameter, value)
        }
    }
}
