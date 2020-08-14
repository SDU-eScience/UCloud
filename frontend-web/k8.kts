//DEPS dk.sdu.cloud:k8-resources:0.1.0
package dk.sdu.cloud.k8

bundle {
    name = "webclient"
    version = "0.38.3"

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_auth_callback
                    prefix: ^/api/auth-callback(/.*)?${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_auth_callback_csrf
                    prefix: ^/api/auth-callback-csrf(/.*)?${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_sync_callback
                    prefix: ^/api/sync-callback(/.*)?${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_app
                    prefix: ^/app(/.*)?${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_assets
                    prefix: ^/assets(/.*)?${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: webclient_favicon
                    prefix: ^/favicon.ico${'$'}
                    prefix_regex: true
                    service: webclient:8080
                    rewrite: ""
                    
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment(injectAllDefaults = false) {
        deployment.spec.replicas = 2
        injectDefaults(tokenValidation = true, refreshToken = true, psql = false)

        serviceContainer.image = "dreg.cloud.sdu.dk/ucloud/webclient:${this@bundle.version}"
    }

    withPostgresMigration(deployment)
}
