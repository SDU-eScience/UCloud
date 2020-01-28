package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSource
import io.fabric8.kubernetes.api.model.SecretVolumeSource
import io.fabric8.kubernetes.api.model.Volume
import io.fabric8.kubernetes.api.model.VolumeMount

fun DeploymentResource.injectConfiguration(name: String) {
    serviceContainer.command.add("--config-dir")
    serviceContainer.command.add("/etc/$name")

    serviceContainer.volumeMounts.add(VolumeMount().apply {
        mountPath = "/etc/$name"
        this.name = name
    })

    volumes.add(Volume().apply {
        this.name = name
        configMap = ConfigMapVolumeSource().apply {
            this.name = name
        }
    })
}

fun DeploymentResource.injectSecret(name: String) {
    serviceContainer.command.add("--config-dir")
    serviceContainer.command.add("/etc/$name")

    serviceContainer.volumeMounts.add(VolumeMount().apply {
        mountPath = "/etc/$name"
        this.name = name
    })

    volumes.add(Volume().apply {
        this.name = name
        secret = SecretVolumeSource().apply {
            optional = false
            secretName = name
        }
    })
}

fun DeploymentResource.injectDefaults(
    tokenValidation: Boolean = true,
    refreshToken: Boolean = true,
    psql: Boolean = true
) {
    if (tokenValidation) injectConfiguration("token-validation")
    if (refreshToken) injectSecret("$name-refresh-token")
    if (psql) injectSecret("$name-psql")
}
