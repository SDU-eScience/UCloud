//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "app-orchestrator"
    version = "2.3.0"

    withAmbassador(null) {
        addSimpleMapping("/api/hpc/jobs")
        addSimpleMapping("/api/hpc/urls")
        addSimpleMapping("/api/app/compute")
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
        injectConfiguration("app-config")
    }

    withPostgresMigration(deployment)

    withConfigMap(name = "app-config", version = "5") {
        data class ComputeBackend(val name: String, val title: String, val useWorkspaces: Boolean)
        val config: Map<String, Any?> = mapOf(
            "app" to mapOf(
                "defaultBackend" to "kubernetes",
                "backends" to listOf(
                    ComputeBackend("kubernetes", "k8s", true)
                )
            )
        )

        addConfig("config.yml", config)
    }
}
