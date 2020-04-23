//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "filesearch"
    version = "1.2.0-projects.2"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                ---
                apiVersion: ambassador/v1
                kind: Mapping
                name: filesearch
                prefix: ^/api/file-search(/.*)?${'$'}
                prefix_regex: true
                service: filesearch:8080
                rewrite: ""
                precedence: 10
                headers:
                  x-no-load: true
                  
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
}
