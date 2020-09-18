package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper

object KubernetesResources {
    val pod = KubernetesResourceLocator(API_GROUP_CORE, "v1", "pods")
    val cronJob = KubernetesResourceLocator("batch", "v1beta1", "cronjobs")
    val daemonSet = KubernetesResourceLocator("apps", "v1", "daemonsets")
    val deployment = KubernetesResourceLocator("apps", "v1", "deployments")
    val job = KubernetesResourceLocator("batch", "v1", "jobs")
    val replicaSet = KubernetesResourceLocator("apps", "v1", "replicasets")
    val statefulSet = KubernetesResourceLocator("apps", "v1", "statefulsets")
}

typealias KubernetesTimestamp = String

data class ObjectMeta(
    val name: String? = null,
    val namespace: String? = null,
    val annotations: Map<String, Any?>? = null,
    val clusterName: KubernetesTimestamp? = null,
    val creationTimestamp: String? = null,
    val deletionGracePeriodSeconds: Int? = null,
    val deletionTimestamp: KubernetesTimestamp? = null,
    val finalizers: List<String>? = null,
    val generateName: String? = null,
    val generation: Int? = null,
    val labels: Map<String, Any?>? = null,
    val managedFields: List<Map<String, Any?>>? = null,
    val ownerReferences: List<Map<String, Any?>>? = null,
    val resourceVersion: String? = null,
    val selfLink: String? = null,
    val uid: String? = null,
)

data class WatchEvent<T>(
    val type: String,
    @get:JsonAlias("object") val theObject: T
)

data class Affinity(
    val nodeAffinity: Affinity? = null,
    val podAffinity: Affinity? = null,
    val podAntiAffinity: Affinity? = null,
) {
    data class Node(
        val preferredDuringSchedulingIgnoredDuringExecution: PreferredSchedulingTerm? = null,
        val requiredDuringSchedulingIgnoredDuringExecution: NodeSelector? = null,
    ) {
        data class PreferredSchedulingTerm(
            val preference: NodeSelectorTerm? = null,
            val weight: Int? = null,
        )

        data class NodeSelectorTerm(
            val matchExpressions: NodeSelectorRequirement? = null,
            val matchFields: NodeSelectorRequirement? = null,
        )

        data class NodeSelectorRequirement(
            val key: String? = null,
            val operator: String? = null,
            val values: List<String>? = null,
        )
    }
    data class Pod(
        val preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        val requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )
    data class PodAnti(
        val preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        val requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    data class WeightedPodAffinityTerm(
        val podAffinityTerm: PodAffinityTerm? = null,
        val weight: Int? = null,
    )

    data class PodAffinityTerm(
        val labelSelector: LabelSelector? = null,
        val namespaces: List<String>? = null,
        val topologyKey: String? = null,
    )
}

data class LabelSelector(
    val matchExpressions: List<LabelSelectorRequirement>? = null,
    val matchLabels: Map<String, Any?>? = null,
)

data class LabelSelectorRequirement(
    val key: String? = null,
    val operator: String? = null,
    val values: List<String>? = null,
)
data class NodeSelector(
    val key: String? = null,
    val operator: String? = null,
    val values: List<String>? = null,
)

data class LocalObjectReference(
    val name: String? = null
)

data class Pod(
    val apiVersion: String = "v1",
    val kind: String = "Pod",
    val metadata: ObjectMeta? = null,
    val spec: Spec? = null,
    val status: Status? = null
) {
    data class Spec(
        val activeDeadlineSeconds: Int? = null,
        val affinity: Affinity? = null,
        val automountServiceAccountToken: Boolean? = null,
        val containers: List<Container>? = null,
        //val dnsConfig: PodDNSConfig?,
        val dnsPolicy: String? = null,
        val enableServiceLinks: Boolean? = null,
        //val ephemeralContainers: List<EphemeralContainer>?,
        //val hostAliases: List<HostAlias>,
        val hostIPC: Boolean? = null,
        val hostNetwork: Boolean? = null,
        val hostPID: Boolean? = null,
        val hostname: String? = null,
        //val imagePullSecrets: List<LocalObjectReference>?,
        val initContainers: List<Container>? = null,
        val nodeName: String? = null,
        val nodeSelector: Map<String, Any?>? = null,
        val overhead: Map<String, Any?>? = null,
        val preemptionPolicy: String? = null,
        val priority: Int? = null,
        val priorityClassName: String? = null,
        val restartPolicy: String? = null,
        val runtimeClassName: String? = null,
        val schedulerName: String? = null,
        val securityContext: PodSecurityContext? = null,
        val serviceAccountName: String? = null,
        val subdomain: String? = null,
        val tolerations: List<Toleration>? = null,
        val volumes: List<Volume>? = null
    )

    data class SpecTemplate(
        val metadata: ObjectMeta? = null,
        val spec: Spec? = null,
    )

    data class PodSecurityContext(
        val fsGroup: Int? = null,
        val fsGroupChangePolicy: String? = null,
        val runAsGroup: Int? = null,
        val runAsNonRoot: Boolean? = null,
        val runAsUser: Int? = null,
        val supplementalGroups: List<Int>? = null,
        //val sysctls: List<Sysctl>?,
        //val windowsOptions: WindowsSecutiyContextOptions?
        //val seLinuxOptions: SELinuxOptions?,
        //val seccompProfile: SeccompProfile?,
    )

    data class Toleration(
        val effect: String? = null,
        val key: String? = null,
        val operator: String? = null,
        val tolerationSeconds: Int? = null,
        val value: String? = null,
    )

    data class Volume(
        val name: String? = null,
        val emptyDir: EmptyDirVolumeSource? = null,
        val configMap: ConfigMapVolumeSource? = null,
        val secret: SecretVolumeSource? = null,
        val flexVolume: FlexVolumeSource? = null,
        val persistentVolumeClaim: PersistentVolumeClaimSource? = null,
    ) {
        data class EmptyDirVolumeSource(
            val medium: String? = null,
            val quantity: String? = null
        )

        data class ConfigMapVolumeSource(
            val defaultMode: Int? = null,
            val items: List<KeyToPath>? = null,
            val name: String? = null,
            val optional: Boolean? = null,
        )

        data class KeyToPath(
            val key: String? = null,
            val mode: Int? = null,
            val path: String? = null,
        )

        data class SecretVolumeSource(
            val defaultMode: Int? = null,
            val items: List<KeyToPath>? = null,
            val optional: Boolean? = null,
            val secretName: String? = null,
        )

        data class FlexVolumeSource(
            val driver: String? = null,
            val fsType: String? = null,
            val options: Map<String, Any?>? = null,
            val readOnly: Boolean? = null,
            val secretRef: LocalObjectReference? = null
        )

        data class PersistentVolumeClaimSource(
            val claimName: String? = null,
            val readOnly: Boolean? = null,
        )
    }

    data class Status(
        val conditions: List<PodCondition>? = null,
        val containerStatuses: List<ContainerStatus>? = null,
        val hostIP: String? = null,
        val initContainerStatuses: List<ContainerStatus>? = null,
        val message: String? = null,
        val phase: String? = null,
        val podIP: String? = null,
        val podIPs: List<PodIP>? = null,
        val quosClass: String? = null,
        val reason: String? = null,
        val startTime: KubernetesTimestamp? = null,
    )

    data class PodCondition(
        val lastProbeTime: KubernetesTimestamp? = null,
        val lastTransitionTime: KubernetesTimestamp? = null,
        val message: String? = null,
        val reason: String? = null,
        val status: String? = null,
        val type: String? = null,
    )

    data class ContainerStatus(
        val containerID: String? = null,
        val image: String? = null,
        val imageID: String? = null,
        val lastState: ContainerState? = null,
        val name: String? = null,
        val ready: Boolean? = null,
        val restartCount: Int? = null,
        val started: Boolean? = null,
        val state: ContainerState? = null,
    )

    data class ContainerState(
        val running: StateRunning? = null,
        val terminated: StateTerminated? = null,
        val waiting: StateWaiting? = null,
    ) {
        data class StateRunning(
            val startedAt: KubernetesTimestamp? = null,
        )

        data class StateTerminated(
            val containerID: String? = null,
            val exitCode: Int? = null,
            val finishedAt: KubernetesTimestamp? = null,
            val message: String? = null,
            val reason: String? = null,
            val signal: Int? = null,
            val startedAt: KubernetesTimestamp? = null,
        )

        data class StateWaiting(
            val message: String? = null,
            val reason: String? = null,
        )
    }

    data class PodIP(
        val ip: String? = null,
    )

    data class Container(
        val args: List<String>? = null,
        val command: List<String>? = null,
        val env: List<EnvVar>? = null,
        val envFrom: List<EnvFromSource>? = null,
        val image: String? = null,
        val imagePullPolicy: String? = null,
        //val lifecycle: Lifecycle?,
        val livenessProbe: Probe? = null,
        val name: String? = null,
        val ports: List<ContainerPort>? = null,
        val readinessProbe: Probe? = null,
        val resources: ResourceRequirements? = null,
        val securityContext: SecurityContext? = null,
        val startupProbe: Probe? = null,
        val stdin: Boolean? = null,
        val stdinOnce: Boolean? = null,
        val terminationMessagePath: String? = null,
        val terminationMessagePolicy: String? = null,
        val tty: Boolean? = null,
        val volumeDevices: List<VolumeDevice>? = null,
        val volumeMounts: List<VolumeMount>? = null,
        val workingDir: String? = null,
    ) {
        data class Probe(
            val exec: ExecAction? = null,
            val failureThreshold: Int? = null,
            val httpGet: HttpGetAction? = null,
            val initialDelaySeconds: Int? = null,
            val periodSeconds: Int? = null,
            val successThreshold: Int? = null,
            val tcpSocket: TCPSocketAction? = null,
            val timeoutSeconds: Int? = null
        )

        data class ContainerPort(
            val containerPort: Int? = null,
            val hostIP: String? = null,
            val hostPort: Int? = null,
            val name: String? = null,
            val protocol: String? = null,
        )

        data class ResourceRequirements(
            val limits: Map<String, Any?>? = null,
            val requests: Map<String, Any?>? = null,
        )

        data class SecurityContext(
            val allowPrivilegeEscalation: Boolean? = null,
            //val capabilities: Capabilities?,
            val privileged: Boolean? = null,
            val procMount: String?,
            val readOnlyRootFilesystem: Boolean? = null,
            val runAsGroup: Int? = null,
            val runAsNonRoot: Boolean? = null,
            val runAsUser: Int? = null,
            //val seLinuxOptions: SELinuxOptions?,
            //val seccompProfile: SeccompProfile?,
            //val windowsOptions: WindowsSecurityContextOptions?,
        )

        data class VolumeDevice(
            val devicePath: String? = null,
            val name: String? = null,
        )

        data class VolumeMount(
            val mountPath: String? = null,
            val mountPropagation: String? = null,
            val name: String? = null,
            val readOnly: Boolean? = null,
            val subPath: String? = null,
            val subPathExpr: String? = null,
        )
    }

    data class ExecAction(val command: List<String>? = null)
    data class HttpGetAction(
        val host: String? = null,
        val httpHeaders: List<Map<String, Any?>>? = null,
        val path: String? = null,
        val port: Any? = null, // String | Int
        val scheme: String? = null
    )
    data class TCPSocketAction(
        val host: String? = null,
        val port: Int? = null,
    )

    data class EnvVar(
        val name: String? = null,
        val value: String? = null,
        val valueFrom: EnvVarSource? = null,
    )

    data class EnvVarSource(
        val configMapKeyRef: ConfigMapKeySelector? = null,
        val fieldRef: ObjectFieldSelector? = null,
        val secretKeyRef: SecretKeySelector? = null,
    ) {
        data class ConfigMapKeySelector(
            val key: String? = null,
            val name: String? = null,
            val optional: Boolean? = null,
        )

        data class ObjectFieldSelector(val fieldPath: String? = null)
        data class SecretKeySelector(
            val key: String? = null,
            val name: String? = null,
            val optional: Boolean? = null,
        )
    }

    data class EnvFromSource(
        val configMapRef: ConfigMapEnvSource? = null,
        val prefix: String? = null,
        val secretRef: SecretEnvSource? = null,
    ) {
        data class ConfigMapEnvSource(
            val name: String? = null,
            val optional: Boolean? = null,
        )
        data class SecretEnvSource(
            val name: String? = null,
            val optional: Boolean? = null,
        )
    }
}

typealias ResourceList = Map<String, String>

inline class KubernetesNode(val raw: JsonNode)
val JsonNode.k8: KubernetesNode get() = KubernetesNode(this)
val KubernetesNode.metadata: ObjectMeta get() = defaultMapper.treeToValue(raw["metadata"])
