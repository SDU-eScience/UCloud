package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.sql.DBContext
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.useAndInvokeAndDiscard
import dk.sdu.cloud.sql.withSession

class MaintenanceService(
    private val db: DBContext,
    private val k8: K8DependenciesImpl
) {
    suspend fun isPaused(): Boolean {
        return db.withSession { session ->
            var isPaused = false
            session
                .prepareStatement(
                    """
                        select paused from ucloud_compute_cluster_state where cluster_id = :cluster  
                    """
                )
                .useAndInvoke(
                    prepare = {
                        bindString("cluster", CLUSTER_NAME)
                    },
                    readRow = { row -> isPaused = row.getBoolean(0) ?: false }
                )

            isPaused
        }
    }

    suspend fun setPauseState(paused: Boolean) {
        db.withSession { session ->
            session
                .prepareStatement(
                    """
                        insert into ucloud_compute_cluster_state (cluster_id, paused) values (:cluster, :paused)
                        on conflict (cluster_id) do update set paused = excluded.paused
                    """
                )
                .useAndInvokeAndDiscard(
                    prepare = {
                        bindString("cluster", CLUSTER_NAME)
                        bindBoolean("paused", paused)
                    }
                )
        }
    }

    suspend fun killJob(jobId: String) {
        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.namespace()

        runCatching {
            k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
        }
    }

    suspend fun drainNode(node: String) {
        val jobIds = k8.client
            .listResources(
                Pod.serializer(),
                KubernetesResources.pod.withNamespace(k8.nameAllocator.namespace()),
                mapOf(
                    "fieldSelector" to "spec.nodeName=$node"
                )
            )
            .mapNotNull {
                k8.nameAllocator.jobNameToJobId(
                    it.metadata?.labels?.get(VOLCANO_JOB_NAME_LABEL)?.toString() ?: return@mapNotNull null
                )
            }
            .toSet()

        for (jobId in jobIds) {
            killJob(jobId)
        }
    }

    suspend fun drainCluster() {
        setPauseState(true)

        k8.client
            .listResources(
                VolcanoJob.serializer(),
                KubernetesResources.volcanoJob.withNamespace(k8.nameAllocator.namespace())
            )
            .forEach {
                killJob(k8.nameAllocator.jobNameToJobId(it.metadata?.name ?: return@forEach))
            }
    }

    companion object {
        const val CLUSTER_NAME = "ucloud"
    }
}
