package dk.sdu.cloud.app.kubernetes.services

import io.fabric8.kubernetes.api.model.LabelSelector
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.networking.NetworkPolicy
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer
import io.fabric8.kubernetes.api.model.networking.NetworkPolicySpec
import io.fabric8.kubernetes.client.KubernetesClient

class NetworkPolicyService(
    private val k8sClient: KubernetesClient,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
    fun createPolicy(jobId: String, peers: List<String>) {
        val labelSelector = LabelSelector().also {
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
                it.podSelector = labelSelector
            })
            .endIngress()

            .addNewEgress()
            .addToTo(NetworkPolicyPeer().also {
                it.podSelector = labelSelector
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
                    it.podSelector = peerSelector
                })
                .endEgress()

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
                        it.podSelector = labelSelector
                    }
                )
                .endIngress()
                .endSpec()
                .done()
        }

        spec
            .withPodSelector(labelSelector)
            .endSpec()
            .done()
    }

    fun deletePolicy(jobId: String) {
        k8sClient.network().networkPolicies().inNamespace(namespace).withName("policy-$jobId").delete()
    }
}
