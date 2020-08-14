//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "storage"
    version = "4.2.1"

    val mountLocation: String = config("mountLocation", "Sub path in volume (e.g. 'test')", "")

    withAmbassador(null) {
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list
                    prefix: ^/*/api/files(/(lookup|stat))?/?${'$'}
                    prefix_regex: true
                    rewrite: ""
                    service: storage:8080
                    timeout_ms: 0
                    method: GET
                    headers:
                      x-no-load: true
                    precedence: 10
                    
                """.trimIndent()
            )
        )
        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_2
                    timeout_ms: 0
                    rewrite: ""
                    prefix: ^/api/files(/.*)?${'$'}
                    prefix_regex: true
                    service: storage:8080
                    use_websocket: true
                    
                """.trimIndent()
            )
        )

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_3
                    timeout_ms: 0
                    rewrite: ""
                    prefix: ^/api/files(/)?${'$'}
                    prefix_regex: true
                    service: storage:8080
                    method: DELETE
                    
                """.trimIndent()
            )
        )
    }

    val deployment = withDeployment {
        deployment.spec.replicas = 1

        injectConfiguration("storage-config")
        injectConfiguration("ceph-fs-config")

        val cephfsVolume = "cephfs"
        serviceContainer.volumeMounts.add(VolumeMount().apply {
            name = cephfsVolume
            mountPath = "/mnt/cephfs"
        })

        volumes.add(Volume().apply {
            name = cephfsVolume
            persistentVolumeClaim = PersistentVolumeClaimVolumeSource().apply {
                claimName = cephfsVolume
            }
        })
    }

    withPostgresMigration(deployment)

    withAdHocJob(
        deployment,
        nameSuffix = "migrate-workspaces",
        additionalArgs = {
            listOf("--migrate-workspaces") + remainingArgs
        }
    )

    withAdHocJob(
        deployment,
        nameSuffix = "migrate-permissions",
        additionalArgs = {
            listOf("--migrate-permissions")
        }
    )

    withAdHocJob(
        deployment,
        nameSuffix = "migrate-favorites",
        additionalArgs = {
            listOf("--migrate-favorites")
        }
    )

    withAdHocJob(
        deployment,
        nameSuffix = "migrate-shares",
        additionalArgs = {
            listOf("--migrate-shares")
        }
    )

    withConfigMap("storage-config") {
        addConfig(
            "config.yml",
            //language=yaml
            """
                storage:
                  fileSystemMount: /mnt/cephfs/$mountLocation
                  filePermissionAcl:
                  - "_share"
                  - "_project-repository"
 
            """.trimIndent()
        )
    }
}
