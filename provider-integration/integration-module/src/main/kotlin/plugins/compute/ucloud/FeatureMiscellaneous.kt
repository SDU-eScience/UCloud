package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.ContainerDescription

/**
 * A plugin which performs miscellaneous tasks
 *
 * These tasks have the following in common:
 *
 * - They can be implemented in only a few lines of code
 * - No other code depends on them
 * - They can all be run near the end of the plugin pipeline
 */
object FeatureMiscellaneous : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val application = resources.findResources(job).application
        val containerConfig = application.invocation.container ?: ContainerDescription()

        if (containerConfig.changeWorkingDirectory) builder.workingDirectory = "/work"
        builder.shouldAllowRoot = containerConfig.runAsRoot

        val customRuntime = pluginConfig.kubernetes.categoryToCustomRuntime[job.specification.product.category]
        if (customRuntime != null) builder.runtime = customRuntime

        if (pluginConfig.kubernetes.priorityClassName != null) {
            val podBuilder = builder as? PodBasedBuilder
            if (podBuilder != null) {
                podBuilder.podSpec.priorityClassName = pluginConfig.kubernetes.priorityClassName
            }
        }

        builder.upsertAnnotation("ucloud.dk/jobId", job.id)

    }
}
