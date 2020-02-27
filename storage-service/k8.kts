//DEPS dk.sdu.cloud:k8-resources:0.1.1
package dk.sdu.cloud.k8

bundle { ctx ->
    name = "storage"
    version = "3.2.13"

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

        services.add(
            AmbassadorMapping(
                """
                    ---
                    apiVersion: ambassador/v1
                    kind: Mapping
                    name: storage_list_4
                    timeout_ms: 0
                    rewrite: ""
                    prefix: ^/api/files/workspaces(/)?${'$'}
                    prefix_regex: true
                    service: storage:8080
                    use_websocket: true                
                    
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

    resources.add(
        DeploymentResource(
            name = "storage-workspace-queue",
            version = version,
            image = "registry.cloud.sdu.dk/sdu-cloud/storage-service:${version}"
        ).apply {
            this.deployment.spec.replicas = 3
            this.serviceContainer.args = this.serviceContainer.args + listOf("--workspace-queue")
            this.serviceContainer.livenessProbe = null

            injectConfiguration("token-validation")
            injectSecret("storage-refresh-token")
            injectSecret("storage-psql")
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
    )

    withPostgresMigration(deployment)

    withAdHocJob(
        deployment,
        nameSuffix = "scan",
        additionalArgs = {
            listOf("--scan") + remainingArgs
        }
    )

    withConfigMap("storage-config") {
        val mountLocation = when (ctx.environment) {
            Environment.PRODUCTION -> "/mnt/cephfs"
            Environment.DEVELOPMENT -> "/mnt/cephfs/dev"
            Environment.TEST -> "/mnt/cephfs/test"
        }

        addConfig(
            "config.yml",
            //language=yaml
            """
                storage:
                  fileSystemMount: $mountLocation
                  filePermissionAcl:
                  - "_share"
 
            """.trimIndent()
        )
    }
}
