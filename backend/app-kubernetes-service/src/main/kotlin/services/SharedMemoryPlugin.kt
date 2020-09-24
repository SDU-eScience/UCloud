package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume

/**
 * A plugin which adds a /dev/shm
 *
 * The size of the shared memory device is set to be limited by the RAM allocated to the job itself. If the job has
 * no RAM reservation attached it will default to 1GB of RAM.
 */
class SharedMemoryPlugin : JobManagementPlugin {
    override suspend fun K8Dependencies.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val sizeInGigs = job.reservation.memoryInGigs ?: 1

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
                        name = "shm",
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
}
