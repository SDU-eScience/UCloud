//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting-compute"
    version = "1.3.0"

    withAmbassador("/api/accounting/compute") {
        addSimpleMapping("/api/products")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }

    withPostgresMigration(deployment)
}
