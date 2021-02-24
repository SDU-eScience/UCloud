package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.ContainerDescription
import dk.sdu.cloud.service.k8.Pod

/**
 * A plugin which performs miscellaneous tasks
 *
 * These tasks have the following in common:
 *
 * - They can be implemented in only a few lines of code
 * - No other code depends on them
 * - They can all be run near the end of the plugin pipeline
 */
object MiscellaneousPlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val application = resources.findResources(job).application
        val containerConfig = application.invocation.container ?: ContainerDescription()
        val tasks = builder.spec?.tasks ?: error("no volcano tasks")

        tasks.forEach { task ->
            val template = task.template ?: error("no task template")
            val pSpec = template.spec ?: error("no template spec")

            // Don't restart the job
            pSpec.restartPolicy = "Never"

            // Disable auto-mount of service account token
            //
            // Even though the associated service account shouldn't have any rights we perform this to be absolutely
            // certain that a normal user cannot use the service account to gain access to the cluster itself.
            pSpec.automountServiceAccountToken = false

            // Change working directory
            //
            // By default UCloud applications use /work as the working directory (as this mounts all the user data)
            // Some applications, however, do not work when their working directory is changed. For that reason we
            // have a flag which allows applications to turn off this behavior.
            if (containerConfig.changeWorkingDirectory) {
                pSpec.containers?.forEach { c ->
                    c.workingDir = "/work"
                }
            }

            // Set security context
            //
            // Note(Dan): This should be used in conjunction with a system which increases encapsulation when
            // containerConfig.runAsRoot == true (e.g. kata containers).
            pSpec.containers?.forEach { c ->
                c.securityContext = Pod.Container.SecurityContext(
                    runAsNonRoot = !containerConfig.runAsRoot,
                    allowPrivilegeEscalation = containerConfig.runAsRoot,
                )
            }
        }
    }
}