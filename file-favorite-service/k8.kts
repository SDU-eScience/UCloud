//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-favorite"
    version = "1.4.6"

    // /api/avatar is added by default
    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_fav_list
                    prefix: ^/api/files/favorite${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-favorite:8080
                    method: GET
                    precedence: 10
                    headers:
                      x-no-load: true
                      
                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_fav
                    prefix: /api/files/favorite
                    rewrite: ""
                    service: file-favorite:8080
                    
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)
}
