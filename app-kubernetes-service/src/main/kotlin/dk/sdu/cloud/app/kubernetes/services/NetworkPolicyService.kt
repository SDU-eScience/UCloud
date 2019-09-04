package dk.sdu.cloud.app.kubernetes.services

import io.fabric8.kubernetes.api.model.LabelSelector
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer
import io.fabric8.kubernetes.client.KubernetesClient

class NetworkPolicyService(
    private val k8sClient: KubernetesClient,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
    fun createPolicy(jobId: String, peers: List<String>) {
        val clientSelector = LabelSelector().also {
            it.matchLabels = hashMapOf(
                JOB_ID_LABEL to jobId,
                ROLE_LABEL to appRole
            )
        }

        val spec = k8sClient.network().networkPolicies()
            .inNamespace(namespace)

            .createNew()
            .withNewMetadata()
            .withName("policy-$jobId")
            .withNamespace(namespace)
            .endMetadata()

            .withNewSpec()
            .addNewIngress()
            .addToFrom(NetworkPolicyPeer().also {
                // Allow connections from other nodes in same job
                it.podSelector = clientSelector
            })
            .endIngress()

            .addNewEgress()
            .addToTo(NetworkPolicyPeer().also {
                // Allow connections to other nodes in same job
                it.podSelector = clientSelector
            })
            .endEgress()

        for (peer in peers) {
            // We ensure that egress is allowed to our peer
            val peerSelector = LabelSelector().also {
                it.matchLabels = hashMapOf(
                    ROLE_LABEL to appRole,
                    JOB_ID_LABEL to peer
                )
            }

            spec
                .addNewEgress()
                .addToTo(NetworkPolicyPeer().also {
                    // (Client egress) Allow connections from client to peer
                    it.podSelector = peerSelector
                })
                .endEgress()
                .addNewIngress()
                .addToFrom(NetworkPolicyPeer().also {
                    // (Client ingress) Allow connections from peer to client
                    it.podSelector = peerSelector
                })
                .endIngress()

            // We also need to ensure that ingress is allowed in our peer (we need to modify their policy for this)
            // We don't need to remove this later since the job id is always unique.
            k8sClient.network().networkPolicies()
                .inNamespace(namespace)
                .withName("policy-$peer")
                .edit()
                .editSpec()
                .addNewIngress()
                .addToFrom(
                    NetworkPolicyPeer().also {
                        // (Peer ingress) Allow connections from client to peer
                        it.podSelector = clientSelector
                    }
                )
                .endIngress()
                .addNewEgress()
                .addToTo(NetworkPolicyPeer().also {
                    // (Peer egress) Allow connections from peer to client
                    it.podSelector = clientSelector
                })
                .endEgress()
                .endSpec()
                .done()
        }

        spec
            .withPodSelector(clientSelector)
            .endSpec()
            .done()
    }

    fun deletePolicy(jobId: String) {
        k8sClient.network().networkPolicies().inNamespace(namespace).withName("policy-$jobId").delete()
    }
}
