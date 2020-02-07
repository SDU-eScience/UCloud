//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "app-orchestrator"
    version = "1.1.1-Test-Duplicate"

    withAmbassador(null) {
        addSimpleMapping("/api/hpc/jobs")
        addSimpleMapping("/api/app/compute")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectConfiguration("app-config")
    }

    withPostgresMigration(deployment)
}
