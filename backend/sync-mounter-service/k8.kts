//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

import io.fabric8.kubernetes.api.model.EnvVar
import io.fabric8.kubernetes.api.model.ServicePort
import io.fabric8.kubernetes.api.model.SecurityContext

bundle {
    name = "sync-mounter"
    version = "2022.1.31"

    val deployment = withDeployment(enableLiveness = false) {
        val cephfsVolume = "cephfs"
        deployment.spec.replicas = 1
        deployment.spec.template.spec.hostname = "syncthing1"
        injectSecret("sync-config", optional = true)

        val syncthingSharedVolume = "syncthing"
        volumes.add(Volume().apply {
            name = syncthingSharedVolume
            emptyDir = EmptyDirVolumeSource()
        })

        containers.add(Container().apply {
            name = "syncthing"
            image = "ghcr.io/linuxserver/syncthing"
            command = listOf(
                "sh", "-c",
                """
                    while [ ! -f /mnt/sync/ready ]; do sleep 0.5; done;
                    /init
                """.trimIndent()
            )

            env = listOf(
                EnvVar().apply {
                    name = "PUID"
                    value = "11042"
                },
                EnvVar().apply {
                    name = "PGID"
                    value = "11042"
                },
                EnvVar().apply {
                    name = "TZ"
                    value = "Europe/Copenhagen"
                }
            )

            workingDir = "/mnt/sync"
            volumeMounts.add(VolumeMount().apply {
                name = syncthingSharedVolume
                mountPath = "/mnt/sync"
                mountPropagation = "Bidirectional"
            })

            volumeMounts.add(VolumeMount().apply {
                name = cephfsVolume
                mountPath = "/config"
                subPath = "syncthing1"
            })

            securityContext = SecurityContext().apply {
                privileged = true
            }
        })

        serviceContainer.securityContext = SecurityContext().apply {
            privileged = true
        }
        serviceContainer.volumeMounts.add(VolumeMount().apply {
            mountPath = "/mnt/sync"
            mountPropagation = "Bidirectional"
            name = syncthingSharedVolume
        })

        serviceContainer.volumeMounts.add(VolumeMount().apply {
            name = cephfsVolume
            mountPath = "/mnt/cephfs"
        })

        volumes.add(Volume().apply {
            name = cephfsVolume
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource().apply {
                claimName = cephfsVolume
            }
        })
    }

    withAmbassador(null) {
        addSimpleMapping("/ucloud/ucloud/sync/mount")
    }
}
