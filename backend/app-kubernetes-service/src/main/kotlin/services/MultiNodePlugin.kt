package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.VerifiedJob
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume

/**
 * A plugin which adds information about other 'nodes' in the job
 *
 * A 'node' in this case has a rather loose definition since there is no guarantee that the individual jobs will be
 * placed on a physically different node. This all depends on the Volcano scheduler.
 *
 * The following information will be added to the `/etc/ucloud` folder:
 *
 * - `rank.txt`: A file containing a single line with a 0-indexed rank within the job
 * - `number_of_nodes.txt`: A file containing a single line with a number indicating how many nodes are participating
 *   in this job.
 * - `nodes.txt`: A file containing a single line of text for every node. Each line will contain a routable
 *   hostname or IP address.
 * - `node-$rank.txt`: A file containing a single line of text for the $rank'th node. Format will be identical to the
 *   one used in `nodes.txt.
 */
object MultiNodePlugin : JobManagementPlugin {
    override suspend fun JobManagement.onCreate(job: VerifiedJob, builder: VolcanoJob) {
        val tasks = builder.spec?.tasks ?: error("no volcano tasks")
        val ucloudVolume = "ucloud-multinode"
        val mountPath = "/etc/ucloud"
        tasks.forEach { t ->
            val template = t.template?.spec ?: error("no template template")
            val initContainers = template.initContainers?.toMutableList() ?: ArrayList()
            initContainers.add(
                Pod.Container().apply {
                    image = "alpine:latest"
                    imagePullPolicy = "IfNotPresent"
                    name = "ucloud-compat"
                    resources = Pod.Container.ResourceRequirements(
                        requests = mapOf(
                            "cpu" to "100m"
                        )
                    )
                    command = listOf(
                        "/bin/sh",
                        "-c",
                        """
                            echo ${'$'}VC_TASK_INDEX > /etc/ucloud/rank.txt;
                            cat /etc/volcano/*_NUM > /etc/ucloud/number_of_nodes.txt;
                            cat /etc/volcano/*.host > /etc/ucloud/nodes.txt;
                            idx=0;
                            while IFS="" read -r p || [ -n "${'$'}p" ]
                            do
                                printf '%s\n' "${'$'}p" > /etc/ucloud/node-${'$'}idx.txt;
                                idx=${'$'}((idx + 1))
                            done < /etc/ucloud/nodes.txt;
                        """
                    )
                    volumeMounts = listOf(
                        Pod.Container.VolumeMount(
                            name = ucloudVolume,
                            readOnly = false,
                            mountPath = mountPath
                        )
                    )
                }
            )
            template.initContainers = initContainers

            template.containers?.forEach { c ->
                val volumeMounts = c.volumeMounts?.toMutableList() ?: ArrayList()
                volumeMounts.add(
                    Pod.Container.VolumeMount(
                        name = ucloudVolume,
                        readOnly = false,
                        mountPath = mountPath
                    )
                )
                c.volumeMounts = volumeMounts
            }

            val volumes = template.volumes?.toMutableList() ?: ArrayList()
            volumes.add(
                Volume(
                    name = ucloudVolume,
                    emptyDir = Volume.EmptyDirVolumeSource()
                )
            )
            template.volumes = volumes
        }
    }
}
