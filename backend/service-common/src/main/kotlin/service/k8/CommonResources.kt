package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper
import java.util.*

object KubernetesResources {
    val pod = KubernetesResourceLocator(API_GROUP_CORE, "v1", "pods")
    val cronJob = KubernetesResourceLocator("batch", "v1beta1", "cronjobs")
    val daemonSet = KubernetesResourceLocator("apps", "v1", "daemonsets")
    val deployment = KubernetesResourceLocator("apps", "v1", "deployments")
    val job = KubernetesResourceLocator("batch", "v1", "jobs")
    val replicaSet = KubernetesResourceLocator("apps", "v1", "replicasets")
    val statefulSet = KubernetesResourceLocator("apps", "v1", "statefulsets")
    val events = KubernetesResourceLocator(API_GROUP_CORE, "v1", "events")
    val namespaces = KubernetesResourceLocator(API_GROUP_CORE, "v1", "namespaces", namespace = NAMESPACE_ANY)
    val persistentVolumes = KubernetesResourceLocator(API_GROUP_CORE, "v1", "persistentvolumes",
        namespace = NAMESPACE_ANY)
    val persistentVolumeClaims = KubernetesResourceLocator(API_GROUP_CORE, "v1", "persistentvolumeclaims")
    val services = KubernetesResourceLocator(API_GROUP_CORE, "v1", "services")
    val networkPolicies = KubernetesResourceLocator("networking.k8s.io", "v1", "networkpolicies")
}

typealias KubernetesTimestamp = Date

data class ObjectMeta(
    var name: String? = null,
    var namespace: String? = null,
    var annotations: Map<String, Any?>? = null,
    var clusterName: KubernetesTimestamp? = null,
    var creationTimestamp: String? = null,
    var deletionGracePeriodSeconds: Int? = null,
    var deletionTimestamp: KubernetesTimestamp? = null,
    var finalizers: List<String>? = null,
    var generateName: String? = null,
    var generation: Int? = null,
    var labels: Map<String, Any?>? = null,
    var managedFields: List<Map<String, Any?>>? = null,
    var ownerReferences: List<Map<String, Any?>>? = null,
    var resourceVersion: String? = null,
    var selfLink: String? = null,
    var uid: String? = null,
)

data class WatchEvent<T>(
    val type: String,
    @get:JsonAlias("object") val theObject: T
)

data class Affinity(
    var nodeAffinity: Affinity? = null,
    var podAffinity: Affinity? = null,
    var podAntiAffinity: Affinity? = null,
) {
    data class Node(
        var preferredDuringSchedulingIgnoredDuringExecution: PreferredSchedulingTerm? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: NodeSelector? = null,
    ) {
        data class PreferredSchedulingTerm(
            var preference: NodeSelectorTerm? = null,
            var weight: Int? = null,
        )

        data class NodeSelectorTerm(
            var matchExpressions: NodeSelectorRequirement? = null,
            var matchFields: NodeSelectorRequirement? = null,
        )

        data class NodeSelectorRequirement(
            var key: String? = null,
            var operator: String? = null,
            var values: List<String>? = null,
        )
    }

    data class Pod(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    data class PodAnti(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    data class WeightedPodAffinityTerm(
        var podAffinityTerm: PodAffinityTerm? = null,
        var weight: Int? = null,
    )

    data class PodAffinityTerm(
        var labelSelector: LabelSelector? = null,
        var namespaces: List<String>? = null,
        var topologyKey: String? = null,
    )
}

data class LabelSelector(
    var matchExpressions: List<LabelSelectorRequirement>? = null,
    var matchLabels: Map<String, Any?>? = null,
)

data class LabelSelectorRequirement(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)

data class NodeSelector(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)

data class LocalObjectReference(
    var name: String? = null
)

data class Pod(
    var apiVersion: String = "v1",
    var kind: String = "Pod",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    data class Spec(
        var activeDeadlineSeconds: Int? = null,
        var affinity: Affinity? = null,
        var automountServiceAccountToken: Boolean? = null,
        var containers: List<Container>? = null,
        //var dnsConfig: PodDNSConfig?,
        var dnsPolicy: String? = null,
        var enableServiceLinks: Boolean? = null,
        //var ephemeralContainers: List<EphemeralContainer>?,
        var hostAliases: List<HostAlias>? = emptyList(),
        var hostIPC: Boolean? = null,
        var hostNetwork: Boolean? = null,
        var hostPID: Boolean? = null,
        var hostname: String? = null,
        //var imagePullSecrets: List<LocalObjectReference>?,
        var initContainers: List<Container>? = null,
        var nodeName: String? = null,
        var nodeSelector: Map<String, Any?>? = null,
        var overhead: Map<String, Any?>? = null,
        var preemptionPolicy: String? = null,
        var priority: Int? = null,
        var priorityClassName: String? = null,
        var restartPolicy: String? = null,
        var runtimeClassName: String? = null,
        var schedulerName: String? = null,
        var securityContext: PodSecurityContext? = null,
        var serviceAccountName: String? = null,
        var subdomain: String? = null,
        var tolerations: List<Toleration>? = null,
        var volumes: List<Volume>? = null
    )

    data class HostAlias(
        var hostnames: List<String>? = emptyList(),
        var ip: String? = null
    )

    data class SpecTemplate(
        var metadata: ObjectMeta? = null,
        var spec: Spec? = null,
    )

    data class PodSecurityContext(
        var fsGroup: Int? = null,
        var fsGroupChangePolicy: String? = null,
        var runAsGroup: Int? = null,
        var runAsNonRoot: Boolean? = null,
        var runAsUser: Int? = null,
        var supplementalGroups: List<Int>? = null,
        //var sysctls: List<Sysctl>?,
        //var windowsOptions: WindowsSecutiyContextOptions?
        //var seLinuxOptions: SELinuxOptions?,
        //var seccompProfile: SeccompProfile?,
    )

    data class Toleration(
        var effect: String? = null,
        var key: String? = null,
        var operator: String? = null,
        var tolerationSeconds: Int? = null,
        var value: String? = null,
    )


    data class Status(
        var conditions: List<PodCondition>? = null,
        var containerStatuses: List<ContainerStatus>? = null,
        var hostIP: String? = null,
        var initContainerStatuses: List<ContainerStatus>? = null,
        var message: String? = null,
        var phase: String? = null,
        var podIP: String? = null,
        var podIPs: List<PodIP>? = null,
        var quosClass: String? = null,
        var reason: String? = null,
        var startTime: KubernetesTimestamp? = null,
    )

    data class PodCondition(
        var lastProbeTime: KubernetesTimestamp? = null,
        var lastTransitionTime: KubernetesTimestamp? = null,
        var message: String? = null,
        var reason: String? = null,
        var status: String? = null,
        var type: String? = null,
    )

    data class ContainerStatus(
        var containerID: String? = null,
        var image: String? = null,
        var imageID: String? = null,
        var lastState: ContainerState? = null,
        var name: String? = null,
        var ready: Boolean? = null,
        var restartCount: Int? = null,
        var started: Boolean? = null,
        var state: ContainerState? = null,
    )

    data class ContainerState(
        var running: StateRunning? = null,
        var terminated: StateTerminated? = null,
        var waiting: StateWaiting? = null,
    ) {
        data class StateRunning(
            var startedAt: KubernetesTimestamp? = null,
        )

        data class StateTerminated(
            var containerID: String? = null,
            var exitCode: Int? = null,
            var finishedAt: KubernetesTimestamp? = null,
            var message: String? = null,
            var reason: String? = null,
            var signal: Int? = null,
            var startedAt: KubernetesTimestamp? = null,
        )

        data class StateWaiting(
            var message: String? = null,
            var reason: String? = null,
        )
    }

    data class PodIP(
        var ip: String? = null,
    )

    data class Container(
        var args: List<String>? = null,
        var command: List<String>? = null,
        var env: List<EnvVar>? = null,
        var envFrom: List<EnvFromSource>? = null,
        var image: String? = null,
        var imagePullPolicy: String? = null,
        //var lifecycle: Lifecycle?,
        var livenessProbe: Probe? = null,
        var name: String? = null,
        var ports: List<ContainerPort>? = null,
        var readinessProbe: Probe? = null,
        var resources: ResourceRequirements? = null,
        var securityContext: SecurityContext? = null,
        var startupProbe: Probe? = null,
        var stdin: Boolean? = null,
        var stdinOnce: Boolean? = null,
        var terminationMessagePath: String? = null,
        var terminationMessagePolicy: String? = null,
        var tty: Boolean? = null,
        var volumeDevices: List<VolumeDevice>? = null,
        var volumeMounts: List<VolumeMount>? = null,
        var workingDir: String? = null,
    ) {
        data class Probe(
            var exec: ExecAction? = null,
            var failureThreshold: Int? = null,
            var httpGet: HttpGetAction? = null,
            var initialDelaySeconds: Int? = null,
            var periodSeconds: Int? = null,
            var successThreshold: Int? = null,
            var tcpSocket: TCPSocketAction? = null,
            var timeoutSeconds: Int? = null
        )

        data class ContainerPort(
            var containerPort: Int? = null,
            var hostIP: String? = null,
            var hostPort: Int? = null,
            var name: String? = null,
            var protocol: String? = null,
        )

        data class ResourceRequirements(
            var limits: Map<String, Any?>? = null,
            var requests: Map<String, Any?>? = null,
        )

        data class SecurityContext(
            var allowPrivilegeEscalation: Boolean? = null,
            //var capabilities: Capabilities?,
            var privileged: Boolean? = null,
            var procMount: String? = null,
            var readOnlyRootFilesystem: Boolean? = null,
            var runAsGroup: Int? = null,
            var runAsNonRoot: Boolean? = null,
            var runAsUser: Int? = null,
            //var seLinuxOptions: SELinuxOptions?,
            //var seccompProfile: SeccompProfile?,
            //var windowsOptions: WindowsSecurityContextOptions?,
        )

        data class VolumeDevice(
            var devicePath: String? = null,
            var name: String? = null,
        )

        data class VolumeMount(
            var mountPath: String? = null,
            var mountPropagation: String? = null,
            var name: String? = null,
            var readOnly: Boolean? = null,
            var subPath: String? = null,
            var subPathExpr: String? = null,
        )
    }

    data class ExecAction(var command: List<String>? = null)
    data class HttpGetAction(
        var host: String? = null,
        var httpHeaders: List<Map<String, Any?>>? = null,
        var path: String? = null,
        var port: Any? = null, // String | Int
        var scheme: String? = null
    )

    data class TCPSocketAction(
        var host: String? = null,
        var port: Int? = null,
    )

    data class EnvVar(
        var name: String? = null,
        var value: String? = null,
        var valueFrom: EnvVarSource? = null,
    )

    data class EnvVarSource(
        var configMapKeyRef: ConfigMapKeySelector? = null,
        var fieldRef: ObjectFieldSelector? = null,
        var secretKeyRef: SecretKeySelector? = null,
    ) {
        data class ConfigMapKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )

        data class ObjectFieldSelector(var fieldPath: String? = null)
        data class SecretKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }

    data class EnvFromSource(
        var configMapRef: ConfigMapEnvSource? = null,
        var prefix: String? = null,
        var secretRef: SecretEnvSource? = null,
    ) {
        data class ConfigMapEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )

        data class SecretEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }
}

data class Event(
    val apiVersion: String = "v1",
    val kind: String = "Event",
    var action: String? = null,
    var count: Int? = null,
    var eventTime: String? = null,
    var firstTimestamp: KubernetesTimestamp? = null,
    var involvedObject: ObjectReference? = null,
    var lastTimestamp: KubernetesTimestamp? = null,
    var message: String? = null,
    var metadata: ObjectMeta? = null,
    var reason: String? = null,
    var related: ObjectReference? = null,
    var reportingComponent: String? = null,
    var reportingInstance: String? = null,
    var series: EventSeries? = null,
    var source: EventSource? = null,
    var type: String? = null,
)

data class ObjectReference(
    val apiVersion: String = "v1",
    val kind: String = "ObjectReference",
    var fieldPath: String? = null,
    var name: String? = null,
    var namespace: String? = null,
    var resourceVersion: String? = null,
    var uid: String? = null,
)

data class EventSource(
    val apiVersion: String = "v1",
    val kind: String = "EventSource",
    var component: String? = null,
    var host: String? = null,
)

data class EventSeries(
    val apiVersion: String = "v1",
    val kind: String = "EventSeries",
    var count: Int? = null,
    var lastObservedTime: String? = null,
)

data class Namespace(
    val apiVersion: String = "v1",
    val kind: String = "Namespace",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    data class Spec(
        var finalizers: List<String>? = null,
    )

    data class Status(
        var conditions: List<NamespaceCondition>? = emptyList(),
        var phase: String? = null,
    )

    data class NamespaceCondition(
        var lastTransitionTime: KubernetesTimestamp? = null,
        var message: String? = null,
        var reason: String? = null,
        var status: String? = null,
        var type: String? = null,
    )
}

data class Volume(
    var name: String? = null,
    var emptyDir: EmptyDirVolumeSource? = null,
    var configMap: ConfigMapVolumeSource? = null,
    var secret: SecretVolumeSource? = null,
    var flexVolume: FlexVolumeSource? = null,
    var persistentVolumeClaim: PersistentVolumeClaimSource? = null,
    var cephfs: CephfsVolumeSource? = null,
) {
    data class EmptyDirVolumeSource(
        var medium: String? = null,
        var sizeLimit: String? = null
    )

    data class ConfigMapVolumeSource(
        var defaultMode: Int? = null,
        var items: List<KeyToPath>? = null,
        var name: String? = null,
        var optional: Boolean? = null,
    )

    data class KeyToPath(
        var key: String? = null,
        var mode: Int? = null,
        var path: String? = null,
    )

    data class SecretVolumeSource(
        var defaultMode: Int? = null,
        var items: List<KeyToPath>? = null,
        var optional: Boolean? = null,
        var secretName: String? = null,
    )

    data class FlexVolumeSource(
        var driver: String? = null,
        var fsType: String? = null,
        var options: Map<String, Any?>? = null,
        var readOnly: Boolean? = null,
        var secretRef: LocalObjectReference? = null
    )

    data class PersistentVolumeClaimSource(
        var claimName: String? = null,
        var readOnly: Boolean? = null,
    )

    data class CephfsVolumeSource(
        var monitors: List<String> = emptyList(),
        var path: String? = null,
        var readOnly: Boolean? = null,
        var secret: LocalObjectReference
    )
}

data class SecretReference(var name: String? = null, var namespace: String? = null)

typealias ResourceList = Map<String, String>

data class Service(
    var apiVersion: String = "v1",
    var kind: String = "Service",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    data class Spec(
        var clueterIP: String? = null,
        var externalIPs: List<String> = emptyList(),
        var externalName: String? = null,
        var externalTrafficPolicy: String? = null,
        var healthCheckNodePort: Int? = null,
        var ipFamily: String? = null,
        var loadBalancerIP: String? = null,
        var loadBalancerSourceRanges: List<String> = emptyList(),
        var ports: List<ServicePort> = emptyList(),
        var publishNotReadyAddresses: Boolean? = null,
        var selector: Map<String, Any?>? = null,
        var sessionAffinity: String? = null,
        var topologyKeys: List<String> = emptyList(),
        var type: String? = null
    )

    data class Status(
        var apiVersion: String = "v1",
        var kind: String = "ServiceStatus",
        var items: List<Service> = emptyList()
    )
}

data class ServicePort(
    var appProtocol: String? = null,
    var name: String? = null,
    var nodePort: Int? = null,
    var port: Int? = null,
    var protocol: String? = null,
    var targetPort: Any? = null
)

data class NetworkPolicy(
    var apiVersion: String = "networking.k8s.io/v1",
    var kind: String = "NetworkPolicy",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null
) {
    data class Spec(
        var egress: List<EgressRule>? = emptyList(),
        var ingress: List<IngressRule>? = emptyList(),
        var podSelector: LabelSelector? = null,
        var policyTypes: List<String>? = emptyList()
    )

    data class EgressRule(
        var ports: List<Port>? = emptyList(),
        var to: List<Peer>? = emptyList()
    )

    data class Port(
        var port: Any? = null,
        var protocol: String? = null
    )

    data class Peer(
        var ipBlock: IPBlock? = null,
        var namespaceSelector: LabelSelector? = null,
        var podSelector: LabelSelector? = null,
    )

    data class IPBlock(
        var cidr: String? = null,
        var except: List<String>? = emptyList()
    )

    data class IngressRule(
        var from: List<Peer>? = emptyList(),
        var ports: List<Port>? = emptyList()
    )
}

data class PersistentVolume(
    val apiVersion: String = "v1",
    val kind: String = "PersistentVolume",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    data class Spec(
        var accessModes: List<String> = emptyList(),
        var capacity: Map<String, Any?>? = null,
        var mountOptions: List<String> = emptyList(),
        var persistentVolumeReclaimPolicy: String? = null,
        var storageClassName: String? = null,
        var volumeMode: String? = null,
        var hostPath: HostPathVolumeSource? = null,
    )

    data class Status(
        var message: String? = null,
        var phase: String? = null,
        var reason: String? = null,
    )
}

data class HostPathVolumeSource(
    var path: String,
    var type: String = ""
)

data class PersistentVolumeClaim(
    var apiVersion: String = "v1",
    var kind: String = "PersistentVolumeClaim",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    data class Spec(
        var accessModes: List<String> = emptyList(),
        var dataSource: TypedLocalObjectReference? = null,
        var resources: Pod.Container.ResourceRequirements? = null,
        var selector: LabelSelector? = null,
        var storageClassName: String? = null,
        var volumeMode: String? = null,
        var volumeName: String? = null,
    )

    data class Status(
        var accessModes: List<String> = emptyList(),
        var capacity: Map<String, Any?> = emptyMap(),
        var phase: String? = null,
    )
}

data class TypedLocalObjectReference(
    var apiGroup: String? = null,
    var kind: String? = null,
    var name: String? = null
)

inline class KubernetesNode(val raw: JsonNode)

val JsonNode.k8: KubernetesNode get() = KubernetesNode(this)
val KubernetesNode.metadata: ObjectMeta get() = defaultMapper.treeToValue(raw["metadata"])
