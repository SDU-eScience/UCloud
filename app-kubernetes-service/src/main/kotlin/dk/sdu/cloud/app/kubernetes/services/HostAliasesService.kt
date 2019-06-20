package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.app.api.ApplicationPeer
import dk.sdu.cloud.calls.RPCException
import io.fabric8.kubernetes.api.model.HostAlias
import io.fabric8.kubernetes.client.KubernetesClient
import io.ktor.http.HttpStatusCode

class HostAliasesService(
    private val k8sClient: KubernetesClient,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
    fun findAliasesForPeers(peers: List<ApplicationPeer>): List<HostAlias> {
        return peers.flatMap { findAliasesForRunningJob(it.jobId, it.name) }
    }

    private fun findAliasesForRunningJob(jobId: String, name: String): List<HostAlias> {
        if (!name.matches(hostNameRegex)) throw RPCException("Bad hostname specified", HttpStatusCode.BadRequest)

        return k8sClient.pods()
            .inNamespace(namespace)
            .withLabel(ROLE_LABEL, appRole)
            .withLabel(JOB_ID_LABEL, jobId)
            .list()
            .items
            .map {
                val ipAddress = it.status.podIP
                val rank = it.metadata.labels[RANK_LABEL]!!.toInt()

                val hostnames = if (rank == 0) listOf(name, "$name-0") else listOf("$name-$rank")

                HostAlias(
                    hostnames,
                    ipAddress
                )
            }
    }

    companion object {
        private val hostNameRegex =
            Regex("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])\$")
    }
}
