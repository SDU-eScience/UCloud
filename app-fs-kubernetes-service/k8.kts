//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-fs-kubernetes"
    version = "0.1.11"

    withAmbassador("/api/app/fs/kubernetes") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 1

        val storageVolume = "app-fs-k8s"
        serviceContainer.volumeMounts.add(VolumeMount().apply {
            name = storageVolume
            mountPath = "/mnt/cephfs"
        })

        volumes.add(Volume().apply {
            name = storageVolume
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource().apply {
                claimName = storageVolume
            }
        })

        injectConfiguration("ceph-fs-config")
    }

    withPostgresMigration(deployment)
}
