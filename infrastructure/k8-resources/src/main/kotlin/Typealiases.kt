package dk.sdu.cloud.k8

// Common type-aliases to avoid having to import from scripts
typealias Container = io.fabric8.kubernetes.api.model.Container
typealias VolumeMount = io.fabric8.kubernetes.api.model.VolumeMount
typealias Volume = io.fabric8.kubernetes.api.model.Volume
typealias EmptyDirVolumeSource = io.fabric8.kubernetes.api.model.EmptyDirVolumeSource
typealias NetworkPolicySpec = io.fabric8.kubernetes.api.model.networking.NetworkPolicySpec
typealias NetworkPolicyEgressRule = io.fabric8.kubernetes.api.model.networking.NetworkPolicyEgressRule
typealias NetworkPolicyPort = io.fabric8.kubernetes.api.model.networking.NetworkPolicyPort
typealias LabelSelector = io.fabric8.kubernetes.api.model.LabelSelector
typealias IntOrString = io.fabric8.kubernetes.api.model.IntOrString
typealias PersistentVolumeClaimVolumeSource = io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource
