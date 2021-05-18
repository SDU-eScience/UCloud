package dk.sdu.cloud.service.k8

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.treeToValue
import dk.sdu.cloud.defaultMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.*

object KubernetesResources {
    val pod = KubernetesResourceLocator(API_GROUP_CORE, "v1", "pods")
    val cronJob = KubernetesResourceLocator("batch", "v1beta1", "cronjobs")
    val daemonSet = KubernetesResourceLocator("apps", "v1", "daemonsets")
    val deployment = KubernetesResourceLocator("apps", "v1", "deployments")
    val job = KubernetesResourceLocator("batch", "v1", "jobs")
    val node = KubernetesResourceLocator(API_GROUP_CORE, "v1", "nodes", namespace = NAMESPACE_ANY)
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

typealias KubernetesTimestamp = String

@Serializable
data class ObjectMeta(
    var name: String? = null,
    var namespace: String? = null,
    var annotations: JsonObject? = null,
    var clusterName: KubernetesTimestamp? = null,
    var creationTimestamp: String? = null,
    var deletionGracePeriodSeconds: Int? = null,
    var deletionTimestamp: KubernetesTimestamp? = null,
    var finalizers: List<String>? = null,
    var generateName: String? = null,
    var generation: Int? = null,
    var labels: JsonObject? = null,
    var managedFields: List<JsonObject>? = null,
    var ownerReferences: List<JsonObject>? = null,
    var resourceVersion: String? = null,
    var selfLink: String? = null,
    var uid: String? = null,
)

@Serializable
data class WatchEvent<T>(
    val type: String,
    @get:JsonAlias("object") @SerialName("object") val theObject: T
)

@Serializable
data class Affinity(
    var nodeAffinity: Affinity? = null,
    var podAffinity: Affinity? = null,
    var podAntiAffinity: Affinity? = null,
) {
    @Serializable
    data class Node(
        var preferredDuringSchedulingIgnoredDuringExecution: PreferredSchedulingTerm? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: NodeSelector? = null,
    ) {
        @Serializable
        data class PreferredSchedulingTerm(
            var preference: NodeSelectorTerm? = null,
            var weight: Int? = null,
        )

        @Serializable
        data class NodeSelectorTerm(
            var matchExpressions: NodeSelectorRequirement? = null,
            var matchFields: NodeSelectorRequirement? = null,
        )

        @Serializable
        data class NodeSelectorRequirement(
            var key: String? = null,
            var operator: String? = null,
            var values: List<String>? = null,
        )
    }

    @Serializable
    data class Pod(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    @Serializable
    data class PodAnti(
        var preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm>? = null,
        var requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm>? = null,
    )

    @Serializable
    data class WeightedPodAffinityTerm(
        var podAffinityTerm: PodAffinityTerm? = null,
        var weight: Int? = null,
    )

    @Serializable
    data class PodAffinityTerm(
        var labelSelector: LabelSelector? = null,
        var namespaces: List<String>? = null,
        var topologyKey: String? = null,
    )
}

@Serializable
data class LabelSelector(
    var matchExpressions: List<LabelSelectorRequirement>? = null,
    var matchLabels: JsonObject? = null,
)

@Serializable
data class LabelSelectorRequirement(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)

@Serializable
data class NodeSelector(
    var key: String? = null,
    var operator: String? = null,
    var values: List<String>? = null,
)

@Serializable
data class LocalObjectReference(
    var name: String? = null
)

@Serializable
data class Pod(
    var apiVersion: String = "v1",
    var kind: String = "Pod",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    @Serializable
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
        var nodeSelector: JsonObject? = null,
        var overhead: JsonObject? = null,
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

    @Serializable
    data class HostAlias(
        var hostnames: List<String>? = emptyList(),
        var ip: String? = null
    )

    @Serializable
    data class SpecTemplate(
        var metadata: ObjectMeta? = null,
        var spec: Spec? = null,
    )

    @Serializable
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

    @Serializable
    data class Toleration(
        var effect: String? = null,
        var key: String? = null,
        var operator: String? = null,
        var tolerationSeconds: Int? = null,
        var value: String? = null,
    )

    @Serializable
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

    @Serializable
    data class PodCondition(
        var lastProbeTime: KubernetesTimestamp? = null,
        var lastTransitionTime: KubernetesTimestamp? = null,
        var message: String? = null,
        var reason: String? = null,
        var status: String? = null,
        var type: String? = null,
    )

    @Serializable
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

    @Serializable
    data class ContainerState(
        var running: StateRunning? = null,
        var terminated: StateTerminated? = null,
        var waiting: StateWaiting? = null,
    ) {
        @Serializable
        data class StateRunning(
            var startedAt: KubernetesTimestamp? = null,
        )

        @Serializable
        data class StateTerminated(
            var containerID: String? = null,
            var exitCode: Int? = null,
            var finishedAt: KubernetesTimestamp? = null,
            var message: String? = null,
            var reason: String? = null,
            var signal: Int? = null,
            var startedAt: KubernetesTimestamp? = null,
        )

        @Serializable
        data class StateWaiting(
            var message: String? = null,
            var reason: String? = null,
        )
    }

    @Serializable
    data class PodIP(
        var ip: String? = null,
    )

    @Serializable
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
        @Serializable
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

        @Serializable
        data class ContainerPort(
            var containerPort: Int? = null,
            var hostIP: String? = null,
            var hostPort: Int? = null,
            var name: String? = null,
            var protocol: String? = null,
        )

        @Serializable
        data class ResourceRequirements(
            var limits: JsonObject? = null,
            var requests: JsonObject? = null,
        )

        @Serializable
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

        @Serializable
        data class VolumeDevice(
            var devicePath: String? = null,
            var name: String? = null,
        )

        @Serializable
        data class VolumeMount(
            var mountPath: String? = null,
            var mountPropagation: String? = null,
            var name: String? = null,
            var readOnly: Boolean? = null,
            var subPath: String? = null,
            var subPathExpr: String? = null,
        )
    }

    @Serializable
    data class ExecAction(var command: List<String>? = null)
    @Serializable
    data class HttpGetAction(
        var host: String? = null,
        var httpHeaders: List<JsonObject>? = null,
        var path: String? = null,
        var port: JsonElement? = null, // String | Int
        var scheme: String? = null
    )

    @Serializable
    data class TCPSocketAction(
        var host: String? = null,
        var port: Int? = null,
    )

    @Serializable
    data class EnvVar(
        var name: String? = null,
        var value: String? = null,
        var valueFrom: EnvVarSource? = null,
    )

    @Serializable
    data class EnvVarSource(
        var configMapKeyRef: ConfigMapKeySelector? = null,
        var fieldRef: ObjectFieldSelector? = null,
        var secretKeyRef: SecretKeySelector? = null,
    ) {
        @Serializable
        data class ConfigMapKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )

        @Serializable
        data class ObjectFieldSelector(var fieldPath: String? = null)
        @Serializable
        data class SecretKeySelector(
            var key: String? = null,
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }

    @Serializable
    data class EnvFromSource(
        var configMapRef: ConfigMapEnvSource? = null,
        var prefix: String? = null,
        var secretRef: SecretEnvSource? = null,
    ) {
        @Serializable
        data class ConfigMapEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )

        @Serializable
        data class SecretEnvSource(
            var name: String? = null,
            var optional: Boolean? = null,
        )
    }
}

@Serializable
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

@Serializable
data class ObjectReference(
    val apiVersion: String = "v1",
    val kind: String = "ObjectReference",
    var fieldPath: String? = null,
    var name: String? = null,
    var namespace: String? = null,
    var resourceVersion: String? = null,
    var uid: String? = null,
)

@Serializable
data class EventSource(
    val apiVersion: String = "v1",
    val kind: String = "EventSource",
    var component: String? = null,
    var host: String? = null,
)

@Serializable
data class EventSeries(
    val apiVersion: String = "v1",
    val kind: String = "EventSeries",
    var count: Int? = null,
    var lastObservedTime: String? = null,
)

@Serializable
data class Namespace(
    val apiVersion: String = "v1",
    val kind: String = "Namespace",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    @Serializable
    data class Spec(
        var finalizers: List<String>? = null,
    )

    @Serializable
    data class Status(
        var conditions: List<NamespaceCondition>? = emptyList(),
        var phase: String? = null,
    )

    @Serializable
    data class NamespaceCondition(
        var lastTransitionTime: KubernetesTimestamp? = null,
        var message: String? = null,
        var reason: String? = null,
        var status: String? = null,
        var type: String? = null,
    )
}

@Serializable
data class Volume(
    var name: String? = null,
    var emptyDir: EmptyDirVolumeSource? = null,
    var configMap: ConfigMapVolumeSource? = null,
    var secret: SecretVolumeSource? = null,
    var flexVolume: FlexVolumeSource? = null,
    var persistentVolumeClaim: PersistentVolumeClaimSource? = null,
    var cephfs: CephfsVolumeSource? = null,
) {
    @Serializable
    data class EmptyDirVolumeSource(
        var medium: String? = null,
        var sizeLimit: String? = null
    )

    @Serializable
    data class ConfigMapVolumeSource(
        var defaultMode: Int? = null,
        var items: List<KeyToPath>? = null,
        var name: String? = null,
        var optional: Boolean? = null,
    )

    @Serializable
    data class KeyToPath(
        var key: String? = null,
        var mode: Int? = null,
        var path: String? = null,
    )

    @Serializable
    data class SecretVolumeSource(
        var defaultMode: Int? = null,
        var items: List<KeyToPath>? = null,
        var optional: Boolean? = null,
        var secretName: String? = null,
    )

    @Serializable
    data class FlexVolumeSource(
        var driver: String? = null,
        var fsType: String? = null,
        var options: JsonObject? = null,
        var readOnly: Boolean? = null,
        var secretRef: LocalObjectReference? = null
    )

    @Serializable
    data class PersistentVolumeClaimSource(
        var claimName: String? = null,
        var readOnly: Boolean? = null,
    )

    @Serializable
    data class CephfsVolumeSource(
        var monitors: List<String> = emptyList(),
        var path: String? = null,
        var readOnly: Boolean? = null,
        var secret: LocalObjectReference
    )
}

@Serializable
data class SecretReference(var name: String? = null, var namespace: String? = null)

typealias ResourceList = JsonObject

@Serializable
data class Service(
    var apiVersion: String = "v1",
    var kind: String = "Service",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null
) {
    @Serializable
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
        var selector: JsonObject? = null,
        var sessionAffinity: String? = null,
        var topologyKeys: List<String> = emptyList(),
        var type: String? = null
    )

    @Serializable
    data class Status(
        var apiVersion: String = "v1",
        var kind: String = "ServiceStatus",
        var items: List<Service> = emptyList()
    )
}

@Serializable
data class ServicePort(
    var appProtocol: String? = null,
    var name: String? = null,
    var nodePort: Int? = null,
    var port: Int? = null,
    var protocol: String? = null,
    var targetPort: JsonElement? = null
)

@Serializable
data class NetworkPolicy(
    var apiVersion: String = "networking.k8s.io/v1",
    var kind: String = "NetworkPolicy",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null
) {
    @Serializable
    data class Spec(
        var egress: List<EgressRule>? = emptyList(),
        var ingress: List<IngressRule>? = emptyList(),
        var podSelector: LabelSelector? = null,
        var policyTypes: List<String>? = emptyList()
    )

    @Serializable
    data class EgressRule(
        var ports: List<Port>? = emptyList(),
        var to: List<Peer>? = emptyList()
    )

    @Serializable
    data class Port(
        var port: JsonElement? = null,
        var protocol: String? = null
    )

    @Serializable
    data class Peer(
        var ipBlock: IPBlock? = null,
        var namespaceSelector: LabelSelector? = null,
        var podSelector: LabelSelector? = null,
    )

    @Serializable
    data class IPBlock(
        var cidr: String? = null,
        var except: List<String>? = emptyList()
    )

    @Serializable
    data class IngressRule(
        var from: List<Peer>? = emptyList(),
        var ports: List<Port>? = emptyList()
    )
}

@Serializable
data class Node(
    var apiVersion: String = "v1",
    var kind: String = "Node",
    var metadata: ObjectMeta? = null,
    var status: Status? = null
) {
    @Serializable
    data class Status(
        var allocatable: Allocatable? = null
    )

    @Serializable
    data class Allocatable(
        var cpu: Int? = null,
        var memory: String? = null
    )
}

@Serializable
data class PersistentVolume(
    val apiVersion: String = "v1",
    val kind: String = "PersistentVolume",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    @Serializable
    data class Spec(
        var accessModes: List<String> = emptyList(),
        var capacity: JsonObject? = null,
        var mountOptions: List<String> = emptyList(),
        var persistentVolumeReclaimPolicy: String? = null,
        var storageClassName: String? = null,
        var volumeMode: String? = null,
        var hostPath: HostPathVolumeSource? = null,
    )

    @Serializable
    data class Status(
        var message: String? = null,
        var phase: String? = null,
        var reason: String? = null,
    )
}

@Serializable
data class HostPathVolumeSource(
    var path: String,
    var type: String = ""
)

@Serializable
data class PersistentVolumeClaim(
    var apiVersion: String = "v1",
    var kind: String = "PersistentVolumeClaim",
    var metadata: ObjectMeta? = null,
    var spec: Spec? = null,
    var status: Status? = null,
) {
    @Serializable
    data class Spec(
        var accessModes: List<String> = emptyList(),
        var dataSource: TypedLocalObjectReference? = null,
        var resources: Pod.Container.ResourceRequirements? = null,
        var selector: LabelSelector? = null,
        var storageClassName: String? = null,
        var volumeMode: String? = null,
        var volumeName: String? = null,
    )

    @Serializable
    data class Status(
        var accessModes: List<String> = emptyList(),
        var capacity: JsonObject? = null,
        var phase: String? = null,
    )
}

@Serializable
data class TypedLocalObjectReference(
    var apiGroup: String? = null,
    var kind: String? = null,
    var name: String? = null
)
