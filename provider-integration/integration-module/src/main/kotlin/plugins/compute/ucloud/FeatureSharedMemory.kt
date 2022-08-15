package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job

/**
 * A plugin which adds a /dev/shm
 *
 * The size of the shared memory device is set to be limited by the RAM allocated to the job itself. If the job has
 * no RAM reservation attached it will default to 1GB of RAM.
 */
object FeatureSharedMemory : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
        val product = resources.findResources(job).product
        val sizeInGigs = product.memoryInGigs ?: 1

        val tasks = builder.spec?.tasks ?: error("no volcano tasks")
        tasks.forEach { t ->
            val template = t.template ?: error("no pod template in task")
            val spec = template.spec ?: error("no pod spec in task")
            spec.containers?.forEach { c ->
                (c.volumeMounts?.toMutableList() ?: ArrayList()).let { volumeMounts ->
                    volumeMounts.add(
                        Pod.Container.VolumeMount(
                            name = "shm",
                            mountPath = "/dev/shm"
                        )
                    )
                    c.volumeMounts = volumeMounts
                }
            }
            (spec.volumes?.toMutableList() ?: ArrayList()).let { volumes ->
                volumes.add(
                    Volume(
                        name = SHARED_MEMORY_VOLUME,
                        emptyDir = Volume.EmptyDirVolumeSource(
                            medium = "Memory",
                            sizeLimit = "${sizeInGigs}Gi"
                        )
                    )
                )
                spec.volumes = volumes
            }
        }
    }

    const val SHARED_MEMORY_VOLUME = "shm"
}
