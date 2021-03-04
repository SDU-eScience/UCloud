package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.orchestrator.api.Job
import dk.sdu.cloud.service.k8.Pod
import dk.sdu.cloud.service.k8.Volume
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    override suspend fun JobManagement.onCreate(job: Job, builder: VolcanoJob) {
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
                        requests = JsonObject(mapOf(
                            "cpu" to JsonPrimitive("100m")
                        ))
                    )
                    command = listOf(
                        "/bin/sh",
                        "-c",
                        //language=sh
                        """
                            CONFIG_DIR="/etc/ucloud";
                            FILE_NODE_COUNT="${'$'}{CONFIG_DIR}/number_of_nodes.txt";
                            FILE_RANK="${'$'}{CONFIG_DIR}/rank.txt";
                            FILE_NODES="${'$'}{CONFIG_DIR}/nodes.txt";
                            FILE_NODE_PREFIX="${'$'}{CONFIG_DIR}/node-"
                            FILE_NODE_SUFFIX=".txt"

                            function trim() {
                              awk '{${'$'}1=${'$'}1};1'
                            }

                            while true
                            do
                              rm -f "${'$'}{CONFIG_DIR}/*";
                              echo ${'$'}VC_TASK_INDEX > ${'$'}FILE_RANK;
                              cat /etc/volcano/*_NUM > ${'$'}FILE_NODE_COUNT;
                              cat /etc/volcano/*.host > ${'$'}FILE_NODES;
                              idx=0;
                              while IFS="" read -r p || [ -n "${'$'}p" ]
                              do
                                  printf '%s\n' "${'$'}p" > "${'$'}{FILE_NODE_PREFIX}${'$'}{idx}${'$'}{FILE_NODE_SUFFIX}";
                                  idx=${'$'}((idx + 1))
                              done < ${'$'}FILE_NODES;

                              # Check that we have all the files and that they appear correct
                              node_count_lines=`cat ${'$'}FILE_NODE_COUNT | trim | wc -l`
                              if [[ ! -f "${'$'}{FILE_NODE_COUNT}" ]] || [ "1" != ${'$'}node_count_lines ]; then
                                echo "${'$'}{FILE_NODE_COUNT} does not look correct"
                                continue;
                              fi

                              node_count_from_file=`cat ${'$'}FILE_NODE_COUNT`
                              nodes_line_count=`cat ${'$'}FILE_NODES | trim | wc -l`
                              if [[ ! -f "${'$'}{FILE_NODES}" ]] || [ ${'$'}node_count_from_file != ${'$'}nodes_line_count ]; then
                                echo "Not enough entries in ${'$'}{FILE_NODES}"
                                continue;
                              fi

                              wildcard=${'$'}(echo "${'$'}{FILE_NODE_PREFIX}"*)
                              node_file_count=`ls -1 ${'$'}wildcard | trim | wc -l`
                              if [ ${'$'}node_file_count != ${'$'}node_count_from_file ]; then
                                echo "Not enough individual node entries (Looking for: ${'$'}{FILE_NODE_PREFIX}*)"
                                continue;
                              fi

                              break;
                            done;
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
