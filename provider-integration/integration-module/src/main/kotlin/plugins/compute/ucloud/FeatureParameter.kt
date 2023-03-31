package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.app.store.api.ArgumentBuilder
import dk.sdu.cloud.app.store.api.buildEnvironmentValue
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.config.VerifiedConfig
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.plugins.storage.ucloud.PathConverter
import dk.sdu.cloud.utils.forEachGraal
import kotlinx.serialization.json.decodeFromJsonElement

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

        val defaultParameters: Map<ApplicationParameter, AppParameterValue> = buildMap {
            for (it in app.parameters) {
                if (it.defaultValue == null) continue
                // NOTE: We might have old data which is not AppParameterValues, those we ignore and just continue
                // without a default value
                val value = runCatching {
                    when (it) {
                        is ApplicationParameter.InputDirectory,
                        is ApplicationParameter.InputFile ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.File.serializer(), it.defaultValue!!)

                        is ApplicationParameter.Bool ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Bool.serializer(), it.defaultValue!!)

                        is ApplicationParameter.FloatingPoint ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.FloatingPoint.serializer(), it.defaultValue!!)

                        is ApplicationParameter.Ingress ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Ingress.serializer(), it.defaultValue!!)

                        is ApplicationParameter.Integer ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Integer.serializer(), it.defaultValue!!)

                        is ApplicationParameter.LicenseServer ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.License.serializer(), it.defaultValue!!)

                        is ApplicationParameter.NetworkIP ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Network.serializer(), it.defaultValue!!)

                        is ApplicationParameter.Peer ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Peer.serializer(), it.defaultValue!!)

                        is ApplicationParameter.Text,
                        is ApplicationParameter.Enumeration ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.Text.serializer(), it.defaultValue!!)

                        is ApplicationParameter.TextArea ->
                            defaultMapper.decodeFromJsonElement(AppParameterValue.TextArea.serializer(), it.defaultValue!!)
                    }
                }.getOrNull() ?: continue

                put(it, value)
            }
        }

        val givenParameters =
            job.specification.parameters!!.map { (paramName, value) ->
                app.parameters.find { it.name == paramName }!! to value
            }.toMap()

        val allParameters = defaultParameters + givenParameters

        builder.command(app.invocation.flatMap { parameter ->
            parameter.buildInvocationList(allParameters, builder = argBuilder)
        })

        app.environment?.forEach { (name, value) ->
            val resolvedValue = value.buildEnvironmentValue(allParameters, builder = argBuilder)
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
