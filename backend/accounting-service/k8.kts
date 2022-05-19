//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "accounting"
    version = "2022.1.54-devel-hippo"

    withAmbassador("/api/accounting") {
        addSimpleMapping("/api/products")
        addSimpleMapping("/api/gifts")
        addSimpleMapping("/api/grant")
        addSimpleMapping("/api/projects")
        addSimpleMapping("/api/providers")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = Configuration.retrieve("defaultScale", "Default scale", 2)
    }

    withPostgresMigration(deployment)
}
