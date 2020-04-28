package dk.sdu.cloud.app.kubernetes.services

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient

class K8NameAllocator(
    val namespace: String,
    val appRole: String,
    private val k8sClient: KubernetesClient
) {
    fun jobName(requestId: String, rank: Int): String = "$JOB_PREFIX$requestId-$rank"

    fun reverseLookupJobName(jobName: String): String? =
        if (jobName.startsWith(JOB_PREFIX)) jobName.removePrefix(JOB_PREFIX).substringBeforeLast('-') else null

    fun listPods(jobId: String?): List<Pod> =
        findPods(jobId).list().items

    fun findPods(jobId: String?) = k8sClient.pods().inNamespace(namespace).withLabel(JOB_ID_LABEL, jobId)

    fun findPodByName(name: String) = k8sClient.pods().inNamespace(namespace).withName(name)

    fun findJobs(jobId: String) =
        k8sClient.batch().jobs()
            .inNamespace(namespace)
            .withLabel(ROLE_LABEL, appRole)
            .withLabel(JOB_ID_LABEL, jobId)

    fun findServices(jobId: String) =
        k8sClient.services()
            .inNamespace(namespace)
            .withLabel(ROLE_LABEL, appRole)
            .withLabel(JOB_ID_LABEL, jobId)

    companion object {
        const val JOB_ID_LABEL = "job-id"
        const val RANK_LABEL = "rank"
        const val JOB_PREFIX = "job-"
        const val ROLE_LABEL = "role"
    }
}
