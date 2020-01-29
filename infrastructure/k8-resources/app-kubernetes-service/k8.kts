package dk.sdu.cloud.k8

//DEPS dk.sdu.cloud:k8-resources:0.1.0

bundle {
    name = "avatar"
    version = "1.3.3"

    withAmbassador {}

    val deployment = withDeployment {
        deployment.spec.replicas = 2

        run {
            // Envoy configuration
            val envoySharedVolume = "envoy"
            volumes.add(Volume().apply {
                name = envoySharedVolume
                emptyDir = EmptyDirVolumeSource()
            })

            containers.add(Container().apply {
                name = "envoy"
                image = "envoyproxy/envoy:v1.11.1"
                command = listOf(
                    "sh", "-c",
                    """
                        while [ ! -f /mnt/shared/envoy/config.yaml ]; do sleep 0.5; done;
                        envoy -c /mnt/shared/envoy/config.yaml
                    """.trimIndent()
                )

                val workingDirectory = "/mnt/shared/envoy"
                workingDir = workingDirectory
                volumeMounts.add(VolumeMount().apply {
                    name = envoySharedVolume
                    mountPath = workingDirectory
                })
            })

            serviceContainer.workingDir = "/mnt/shared"
            serviceContainer.volumeMounts.add(VolumeMount().apply {
                mountPath = serviceContainer.workingDir
                name = envoySharedVolume
            })
        }

        // Service account is needed for this service to schedule user jobs
        deployment.spec.template.spec.serviceAccountName = "app-kubernetes"
    }

    withPostgresMigration(deployment)

    withNetworkPolicy("app-policy") {
        policy.spec = NetworkPolicySpec().apply {
            podSelector = LabelSelector().apply {
                matchLabels = mapOf("role" to "sducloud-app")
            }

            ingress = emptyList()
            egress = listOf(
                allowPortEgress(
                    listOf(
                        PortAndProtocol(53, NetworkProtocol.TCP),
                        PortAndProtocol(53, NetworkProtocol.UDP)
                    )
                ),

                allowEgressTo(
                    listOf(
                        EgressToPolicy(
                            "0.0.0.0/0",
                            listOf(
                                "10.0.0.0/8",
                                "172.16.0.0/12",
                                "192.168.0.0/16"
                            )
                        )
                    )
                ),

                // allow tek-ansys.tek.c.sdu.dk
                allowEgressTo(listOf(EgressToPolicy("10.144.4.166/32"))),

                // allow tek-comsol0a.tek.c.sdu.dk
                allowEgressTo(listOf(EgressToPolicy("10.144.4.169/32")))
            )
        }
    }

    withNetworkPolicy("app-allow-proxy") {
        TODO()
    }
}
