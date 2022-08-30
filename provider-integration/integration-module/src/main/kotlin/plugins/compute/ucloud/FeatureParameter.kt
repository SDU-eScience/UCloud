package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.ArgumentBuilder
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.plugins.storage.ucloud.PathConverter
import dk.sdu.cloud.utils.forEachGraal

/**
 * A plugin which takes information from [ApplicationInvocationDescription.parameters] and makes the information
 * available to the user-container
 *
 * Concretely this means that the following changes will be made:
 *
 * - The container will receive a new command
 * - Environment variables will be initialized with values from the user
 */
class FeatureParameter(
    private val pluginContext: PluginContext,
    private val pathConverter: PathConverter,
) : JobFeature {
    private val argBuilder = OurArgBuilder(pathConverter, pluginContext)

    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        val app = resources.findResources(job).application.invocation
        val givenParameters =
            job.specification.parameters!!.mapNotNull { (paramName, value) ->
                app.parameters.find { it.name == paramName }!! to value
            }.toMap()

        builder.command(app.invocation.flatMap { parameter ->
            parameter.buildInvocationList(givenParameters, builder = argBuilder)
        })

        app.environment?.forEach { (name, value) ->
            val resolvedValue = value.buildEnvironmentValue(givenParameters, builder = argBuilder)
            if (resolvedValue != null) {
                builder.environment(name, resolvedValue)
            }
        }

        val openedFile = job.specification.openedFile
        if (openedFile != null) {
            val lastComponents = pathConverter.ucloudToInternal(UCloudFile.create(openedFile)).components().takeLast(2)
            builder.environment(
                "UCLOUD_OPEN_WITH_FILE",
                joinPath("/work", *lastComponents.toTypedArray()).removeSuffix("/")
            )
        }
    }
}

private class OurArgBuilder(
    private val pathConverter: PathConverter,
    private val pluginContext: PluginContext,
) : ArgumentBuilder {
    override suspend fun build(parameter: ApplicationParameter, value: AppParameterValue): String {
        return when (parameter) {
            is ApplicationParameter.InputFile, is ApplicationParameter.InputDirectory -> {
                val file = (value as AppParameterValue.File)
                val components = pathConverter.ucloudToInternal(UCloudFile.create(file.path)).components()
                if (components.isEmpty()) {
                    return ArgumentBuilder.Default.build(parameter, value)
                }
                joinPath("/work", components[components.lastIndex]).removeSuffix("/")
            }

            is ApplicationParameter.LicenseServer -> {
                val licensePlugins = pluginContext.config.plugins.licenses.values

                var licenseString: String? = null
                licensePlugins.forEachGraal { plugin ->
                    if (licenseString != null) return@forEachGraal

                    with(pluginContext) {
                        with(plugin) {
                            licenseString = runCatching { buildParameter(value as AppParameterValue.License) }.getOrNull()
                        }
                    }
                }

                licenseString ?: throw RPCException("Could not resolve license", HttpStatusCode.NotFound)
            }

            else -> ArgumentBuilder.Default.build(parameter, value)
        }
    }
}
