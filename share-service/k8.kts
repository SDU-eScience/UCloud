//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "share"
    version = "1.6.6"

    withAmbassador("/api/shares") {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: share_list_files
                    prefix: ^/api/shares/list-files${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: share:8080
                    method: GET
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

    withPostgresMigration(deployment)
}
