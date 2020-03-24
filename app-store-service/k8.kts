//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-store"
    version = "0.13.0"

    withAmbassador(null) {
        addSimpleMapping("/api/hpc")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
        injectSecret("elasticsearch-credentials")
    }

    withPostgresMigration(deployment)
    withAdHocJob(deployment, "app-to-elastic", { listOf("--run-script", "--migrate-apps-to-elastic") })
    withAdHocJob(deployment, "move-tags", { listOf("--move") })
}
