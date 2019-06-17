package dk.sdu.cloud.app.kubernetes.services

import io.fabric8.kubernetes.api.model.LabelSelector
import io.fabric8.kubernetes.api.model.networking.NetworkPolicyPeer
import io.fabric8.kubernetes.client.KubernetesClient

class NetworkPolicyService(
    private val k8sClient: KubernetesClient,
    private val namespace: String = "app-kubernetes",
    private val appRole: String = "sducloud-app"
) {
    fun createPolicy(jobId: String) {
        val labelSelector = LabelSelector().also {
            it.matchLabels = hashMapOf(
                JOB_ID_LABEL to jobId,
                ROLE_LABEL to appRole
            )
        }

        k8sClient.network().networkPolicies()
            .createNew()
            //
            .withNewMetadata()
            .withName("policy-$jobId")
            .withNamespace(namespace)
            .endMetadata()
            //
            .withNewSpec()
            .addNewIngress()
            .addToFrom(NetworkPolicyPeer().also {
                it.podSelector = labelSelector
            })
            .endIngress()
            //
            .addNewEgress()
            .addToTo(NetworkPolicyPeer().also {
                it.podSelector = labelSelector
            })
            .endEgress()
            //
            .withPodSelector(labelSelector)
            //
            .endSpec()
            .done()
    }

    fun deletePolicy(jobId: String) {
        k8sClient.network().networkPolicies().inNamespace(namespace).withName("policy-$jobId").delete()
    }
}
