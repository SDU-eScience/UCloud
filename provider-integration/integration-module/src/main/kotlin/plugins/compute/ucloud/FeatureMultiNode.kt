package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.app.orchestrator.api.Job

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
object FeatureMultiNode : JobFeature {
    override suspend fun JobManagement.onCreate(job: Job, builder: ContainerBuilder) {
        if (!builder.supportsSidecar()) return

        val ucloudVolume = "ucloud-multinode"
        val mountPath = "/etc/ucloud"
        builder.mountSharedVolume(ucloudVolume, mountPath)
        builder.sidecar("ucloud-compat") {
            mountSharedVolume(ucloudVolume, mountPath)

            image("alpine:latest")
            vCpuMillis = 100
            memoryMegabytes = 64
            command(listOf(
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
            ))
        }
    }
}
