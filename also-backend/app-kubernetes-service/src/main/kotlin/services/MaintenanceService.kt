package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.kubernetes.services.volcano.VOLCANO_JOB_NAME_LABEL
import dk.sdu.cloud.app.kubernetes.services.volcano.VolcanoJob
import dk.sdu.cloud.app.kubernetes.services.volcano.volcanoJob
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.k8.*

class MaintenanceService(
    private val db: DBContext,
    private val k8: K8Dependencies
) {
    suspend fun isPaused(): Boolean {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("cluster", CLUSTER_NAME)
                    },
                    """
                        select paused from cluster_state where cluster_id = :cluster  
                    """
                )
                .rows
                .singleOrNull()
                ?.getBoolean(0)
                ?: false
        }
    }

    suspend fun setPauseState(paused: Boolean) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("cluster", CLUSTER_NAME)
                        setParameter("paused", paused)
                    },
                    """
                        insert into cluster_state (cluster_id, paused) values (:cluster, :paused)
                        on conflict (cluster_id) do update set paused = excluded.paused
                    """
                )
        }
    }

    suspend fun killJob(jobId: String) {
        val name = k8.nameAllocator.jobIdToJobName(jobId)
        val namespace = k8.nameAllocator.jobIdToNamespace(jobId)

        runCatching {
            k8.client.deleteResource(KubernetesResources.volcanoJob.withNameAndNamespace(name, namespace))
        }
    }

    suspend fun drainNode(node: String) {
        val jobIds = k8.client
            .listResources<Pod>(
                KubernetesResources.pod.withNamespace(NAMESPACE_ANY),
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
            .listResources<VolcanoJob>(
                KubernetesResources.volcanoJob.withNamespace(NAMESPACE_ANY)
            )
            .forEach {
                killJob(k8.nameAllocator.jobNameToJobId(it.metadata?.name ?: return@forEach))
            }
    }

    companion object {
        const val CLUSTER_NAME = "ucloud"
    }
}