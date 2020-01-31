package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ObjectMeta

fun checkVersion(expectedVersion: String, metadata: ObjectMeta?): Boolean {
    val k8Version = metadata?.annotations?.get(UCLOUD_VERSION_ANNOTATION) ?: return false
    return k8Version == expectedVersion
}
