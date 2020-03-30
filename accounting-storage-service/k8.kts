//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting-storage"
    version = "1.3.0"

    withAmbassador("/api/accounting/storage") {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withCronJob(deployment, "0 */3 * * *", listOf("--scan")) {}
    withAdHocJob(deployment, "scan-now", { listOf("--scan") }) {}
}
