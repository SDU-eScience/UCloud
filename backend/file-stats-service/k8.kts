//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-stats"
    version = "2.2.0"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_stats
                    prefix: ^/api/files/stats(/.*)?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-stats:8080
                    
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
}
