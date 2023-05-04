package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.app.store.api.AppParameterValue
import dk.sdu.cloud.app.store.api.ApplicationParameter
import dk.sdu.cloud.config.ConfigSchema

object FeatureModules : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        if (builder !is PodBasedBuilder) return
        val modules = pluginConfig.modules ?: return
        val app = resources.findResources(job).application.invocation
        val appModules = app.modules
        if (appModules != null && appModules.optional.isNotEmpty()) {
            builder.environment("UCLOUD_MODULES_ROOT", appModules.mountPath)

            val modulesToMount = appModules.optional.distinct().mapNotNull { appRequested ->
                modules.entries.find { mod -> mod.name == appRequested }
            }

            val normalizedMountPath = appModules.mountPath.removeSuffix("/") + "/"

            for (module in modulesToMount) {
                mount(module, normalizedMountPath, builder)
            }
        }

        val replacements = HashMap<String, String>()
        val volumeIterator = builder.volumes.iterator()
        while (volumeIterator.hasNext()) {
            val volume = volumeIterator.next()
            val name = volume.name ?: continue
            if (name.startsWith("module-")) {
                val duplicate = builder.volumes.find {
                    !(it.name ?: "").startsWith("module-") && (
                            (volume.hostPath != null && volume.hostPath?.path == it.hostPath?.path) ||
                                    (volume.persistentVolumeClaim != null && volume.persistentVolumeClaim?.claimName == it.persistentVolumeClaim?.claimName)
                            )
                }

                if (duplicate != null) {
                    volumeIterator.remove()
                    replacements[name] = duplicate.name ?: ""
                }
            }
        }

        for (mount in builder.volumeMounts) {
            val replacement = replacements[mount.name] ?: continue
            mount.name = replacement
        }
    }

    suspend fun JobManagement.transformParameters(
        job: Job,
        builder: ContainerBuilder,
        parameters: MutableMap<ApplicationParameter, AppParameterValue>
    ) {
        if (builder !is PodBasedBuilder) return
        val modules = pluginConfig.modules ?: return
        val parametersToMatch = modules.legacy.parametersToMatch
        if (parametersToMatch.isEmpty()) return
        val app = resources.findResources(job).application.invocation
        val mountPath = app.modules?.mountPath ?: "/opt/ucloud-modules"
        val normalizedMountPath = mountPath.removeSuffix("/") + "/"

        outer@for (pDefinition in app.parameters) {
            if (!pDefinition.optional) continue
            if (pDefinition in parameters) continue

            val title = pDefinition.title ?: pDefinition.name
            for (matcher in parametersToMatch) {
                if (matcher.name != pDefinition.name) continue
                if (matcher.title != title) continue
                val matchingModule = modules.entries.find { it.name == matcher.moduleToMount } ?: continue

                // NOTE(Dan): FeatureParameter is aware that we might send it a text parameter for a file input
                parameters[pDefinition] = AppParameterValue.Text(
                    mount(matchingModule, normalizedMountPath, builder),
                )
                break@outer
            }
        }
    }

    private fun mount(
        module: ConfigSchema.Plugins.Jobs.UCloud.ModuleEntry,
        normalizedMountPath: String,
        builder: PodBasedBuilder,
    ): String {
        val volumeName = "module-${module.name}"
        builder.volumes.add(
            Volume(
                volumeName,
                hostPath = module.hostPath?.let { path ->
                    Volume.HostPathSource(path)
                },
                persistentVolumeClaim = module.volumeClaim?.let { claimName ->
                    Volume.PersistentVolumeClaimSource(claimName)
                }
            )
        )

        val mountPath = normalizedMountPath + module.name
        if (builder.volumeMounts.any { it.mountPath == mountPath }) return mountPath

        builder.volumeMounts.add(
            Pod.Container.VolumeMount(
                name = volumeName,
                mountPath = mountPath,
                subPath = module.internalPath,
                readOnly = true,
            )
        )

        return mountPath
    }
}
