//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "file-favorite"
    version = "1.5.3"

    // /api/avatar is added by default
    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: file_fav
                    prefix: ^/api/files/favorite/(.*)?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: file-favorite:8080
                    precedence: 10
                    
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1
    }

    withPostgresMigration(deployment)

    withAdHocJob(
        deployment,
        nameSuffix = "migrate-metadata",
        additionalArgs = { listOf("--migrate-metadata") }
    )
}
