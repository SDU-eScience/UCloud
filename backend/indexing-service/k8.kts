//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "indexing"
    version = "1.17.0"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        injectSecret("elasticsearch-credentials")
        injectConfiguration("ceph-fs-config")

        val cephfsVolume = "cephfs"
        serviceContainer.volumeMounts.add(VolumeMount().apply {
            name = cephfsVolume
            mountPath = "/mnt/cephfs"
            readOnly = true
        })

        volumes.add(Volume().apply {
            name = cephfsVolume
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource().apply {
                claimName = cephfsVolume
            }
        })
    }

    val deploymentWithMount = DeploymentResource(
        name = name,
        version = version,
        image = "dreg.cloud.sdu.dk/ucloud/indexing-service:${version}"
    ).apply {
        this.serviceContainer.args = this.serviceContainer.args
        this.serviceContainer.livenessProbe = null

        injectDefaults()
        injectSecret("elasticsearch-credentials")
        injectConfiguration("ceph-fs-config")

        val cephfsVolume = "cephfs"
        serviceContainer.volumeMounts.add(VolumeMount().apply {
            name = cephfsVolume
            mountPath = "/mnt/cephfs"
            readOnly = true
        })

        volumes.add(Volume().apply {
            name = cephfsVolume
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource().apply {
                claimName = cephfsVolume
            }
        })
    }

    withPostgresMigration(deployment)
    withCronJob(deploymentWithMount, "0 */12 * * *", listOf("--scan")) {
    }

    withAdHocJob(deploymentWithMount, "scan-now", { listOf("--scan", "--debug") }) {
    }

    val numberOfShards = Configuration.retrieve("indexing.numberOfShards", "number of shared for index", 5)
    val numberOfReplicas = Configuration.retrieve("indexing.numberOfReplicas", "number of replicas for index", 2)

    withMigration(
        deployment,
        listOf("--create-index", "$numberOfShards", "$numberOfReplicas"),
        "create-index",
        version = "1.15.10"
    )
}
