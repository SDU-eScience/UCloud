//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "indexing"
    version = "1.16.3"

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
        image = "registry.cloud.sdu.dk/sdu-cloud/indexing-service:${version}"
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

    val numberOfShards = when (ctx.environment) {
        Environment.DEVELOPMENT -> 5
        Environment.PRODUCTION -> 5
        Environment.TEST -> 2
    }

    val numberOfReplicas = when (ctx.environment) {
        Environment.PRODUCTION -> 2
        Environment.DEVELOPMENT -> 2
        Environment.TEST -> 1
    }

    withMigration(
        deployment,
        listOf("--create-index", "$numberOfShards", "$numberOfReplicas"),
        "create-index",
        version = "1.15.10"
    )
}
