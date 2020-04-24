//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-gateway"
    version = "1.5.0-projects.0"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_gw_list
                    prefix: ^/*/api/files(/(lookup|stat))?/?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-gateway:8080
                    timeout_ms: 30000
                    method: GET
                    precedence: 9
 
                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_fav_list_gw
                    prefix: ^/api/files/favorite/?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-gateway:8080
                    timeout_ms: 30000
                    method: GET
                    precedence: 9
                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: filesearch_gw
                    prefix: ^/api/file-search(/.*)?/?${'$'}
                    prefix_regex: true
                    service: file-gateway:8080
                    timeout_ms: 30000
                    rewrite: ""
                    precedence: 9

                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: share_file_list_gw
                    prefix: ^/api/shares/list-files/?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-gateway:8080
                    timeout_ms: 30000
                    method: GET
                    precedence: 9
     
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 2
    }
}
