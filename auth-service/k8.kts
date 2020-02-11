//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "auth"
    version = "1.27.2-MAIL-TEST-4"

    withAmbassador("/auth") {
        addSimpleMapping("/api/sla")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
        injectSecret("auth-certs")
        injectSecret("auth-wayf", "/etc/wayf-certs")
        injectSecret("auth-wayf-config")
        injectConfiguration("auth-config")
    }

    withPostgresMigration(deployment)
    withCronJob(deployment, "0 2 * * 1", listOf("--tokenScan")) {}
}
