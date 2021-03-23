//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "app-orchestrator"
    version = "2.6.1"
    val domain: String = config("domain", "The provider domain")
    val port: Int = config("port", "The provider port", 443)
    val https: Boolean = config("https", "Provider https", true)

    withAmbassador(null) {
        addSimpleMapping("/api/hpc/jobs")
        addSimpleMapping("/api/hpc/urls")
        addSimpleMapping("/api/app/compute")
        addSimpleMapping("/api/jobs")
        addSimpleMapping("/api/ingresses")
        addSimpleMapping("/api/licenses")
        addSimpleMapping("/api/networkips")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectConfiguration("app-config")
    }

    withPostgresMigration(deployment)

    withConfigMap(name = "app-config", version = "6") {
        val config: Map<String, Any?> = mapOf(
            "app" to mapOf(
                "provider" to mapOf(
                    "domain" to domain,
                    "port" to port,
                    "https" to https
                )
            )
        )

        addConfig("config.yml", config)
    }
}
