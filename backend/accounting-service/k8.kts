//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting"
    version = "1.4.2"

    withAmbassador("/api/accounting") {
        addSimpleMapping("/api/products")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)

    withAdHocJob(deployment, "instant-check-wallets", { listOf("--low-funds-check")}) {}
    withCronJob(deployment, "0 */12 * * *", listOf("--low-funds-check"), name="check-wallets") {}
}
