package dk.sdu.cloud.app.kubernetes.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.app.kubernetes.services.K8NameAllocator.Companion.JOB_ID_LABEL
import dk.sdu.cloud.app.kubernetes.services.K8NameAllocator.Companion.ROLE_LABEL
import io.fabric8.kubernetes.api.model.LabelSelector
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer

class NetworkPolicyService(private val k8: K8Dependencies) {
    fun createPolicy(jobId: String, peers: List<String>) {
        val clientSelector = LabelSelector().also {
            it.matchLabels = hashMapOf(
                JOB_ID_LABEL to jobId,
                ROLE_LABEL to k8.nameAllocator.appRole
            )
        }

        val spec = k8.client.network().networkPolicies()
            .inNamespace(k8.nameAllocator.namespace)

            .createOrReplaceWithNew()
            .withNewMetadata()
            .withName("policy-$jobId")
            .withNamespace(k8.nameAllocator.namespace)
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
                    ROLE_LABEL to k8.nameAllocator.appRole,
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
            k8.client.network().networkPolicies()
                .inNamespace(k8.nameAllocator.namespace)
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

        val networkPolicy = spec
            .withPodSelector(clientSelector)
            .endSpec()
            .done()

        log.debug("Creating network policy with name: policy-$jobId")
        log.info(defaultMapper.writeValueAsString(networkPolicy))

    }

    fun deletePolicy(jobId: String) {
        k8.client.network().networkPolicies().inNamespace(k8.nameAllocator.namespace).withName("policy-$jobId").delete()
    }

    companion object : Loggable {
        override val log = logger()
    }
}
