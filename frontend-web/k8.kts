//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "webclient"
    version = "2021.3.0-alpha22"

    withAmbassador(null) {
        addSimpleMapping("/api/auth-callback")
        addSimpleMapping("/api/auth-callback-csrf")
        addSimpleMapping("/api/sync-callback")
        addSimpleMapping("/app")
        addSimpleMapping("/assets")
        addSimpleMapping("/favicon.ico")
        addSimpleMapping("/favicon.svg")
        addSimpleMapping("/AppVersion.txt")
    }

    val deployment = withDeployment(injectAllDefaults = false) {
        deployment.spec.replicas = 2
        injectDefaults(tokenValidation = true, refreshToken = true, psql = false)

        serviceContainer.livenessProbe = null
        serviceContainer.image = "dreg.cloud.sdu.dk/ucloud/webclient:${this@bundle.version}"
    }

    withPostgresMigration(deployment)
}
