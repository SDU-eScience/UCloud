package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.app.store.api.ApplicationInvocationDescription
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
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
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val app = job.application.invocation
        val givenParameters =
            job.jobInput.asMap().mapNotNull { (paramName, value) ->
                if (value != null) {
                    app.parameters.find { it.name == paramName }!! to value
                } else {
                    null
                }
            }.toMap()

        (builder.spec?.tasks ?: error("no volcano tasks")).forEach { task ->
            val containers = task.template?.spec?.containers ?: error("no containers in task")
            containers.forEach { container ->
                container.command = run {
                    app.invocation.flatMap { parameter ->
                        parameter.buildInvocationList(givenParameters)
                    }
                }

                container.env = run {
                    val envVars = ArrayList<Pod.EnvVar>()
                    job.application.invocation.environment?.forEach { (name, value) ->
                        val resolvedValue = value.buildEnvironmentValue(givenParameters)
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