//DEPS dk.sdu.cloud:k8-resources:0.1.2
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "app-orchestrator"
    version = "2.2.0"

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

    withConfigMap(name = "app-config", version = "4") {
        data class ComputeBackend(val name: String, val title: String, val useWorkspaces: Boolean)
        val gpuWhitelist = Configuration.retrieve<List<String>>("app-orchestrator.gpuWhiteList", "GPU whitelist", emptyList())
        val machines = Configuration.retrieve<List<Map<String, Any?>>>(
            "app-orchestrator.machines",
            "List of machines (name: String, cpu: Int?, memoryInGigs: Int?, gpu: Int?)"
        )

        val config: Map<String, Any?> = mapOf(
            "app" to mapOf(
                "defaultBackend" to "kubernetes",
                "gpuWhitelist" to gpuWhitelist,
                "backends" to listOf(
                    ComputeBackend("kubernetes", "k8s", true)
                ),
                "machines" to machines
            )
        )

        addConfig("config.yml", config)
    }
}
