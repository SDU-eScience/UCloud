package dk.sdu.cloud.k8

//DEPS dk.sdu.cloud:k8-resources:0.1.2

bundle { ctx ->
    name = "app-kubernetes"
    version = "0.18.0-application-urls.2"

    withAmbassador(pathPrefix = null) {
        addSimpleMapping("/api/app/compute/kubernetes")
    }

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
                mountPath = "/mnt/shared/envoy"
                name = envoySharedVolume
            })
        }

        // Service account is needed for this service to schedule user jobs
        deployment.spec.template.spec.serviceAccountName = this@bundle.name

        injectConfiguration("app-kubernetes")
        injectConfiguration("ceph-fs-config")
    }

    withPostgresMigration(deployment)

    val networkPolicyPodSelector = mapOf("role" to "sducloud-app")
    withNetworkPolicy("app-policy", version = "2") {
        policy.metadata.namespace = "app-kubernetes"

        policy.spec = NetworkPolicySpec().apply {
            podSelector = LabelSelector().apply {
                matchLabels = networkPolicyPodSelector
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
                allowEgressTo(listOf(EgressToPolicy("10.144.4.169/32"))),

                // coumputational biology server SDU (requested by Emiliano)
                allowEgressTo(listOf(EgressToPolicy("10.137.1.93/32")))
            )
        }
    }

    withNetworkPolicy("app-allow-proxy") {
        policy.metadata.namespace = "app-kubernetes"

        policy.spec = NetworkPolicySpec().apply {
            podSelector = LabelSelector().apply {
                matchLabels = networkPolicyPodSelector
            }

            ingress = listOf(
                allowFromPods(mapOf("app" to "app-kubernetes"), null)
            )
        }
    }

    withClusterServiceAccount {
        addRule(
            apiGroups = listOf(""),
            resources = listOf("pods", "pods/log", "pods/portforward", "pods/exec"),
            verbs = listOf("*")
        )

        addRule(
            apiGroups = listOf("batch", "extensions"),
            resources = listOf("jobs", "networkpolicies"),
            verbs = listOf("*")
        )

        addRule(
            apiGroups = listOf("networking.k8s.io"),
            resources = listOf("networkpolicies"),
            verbs = listOf("*")
        )
    }

    val prefix: String = when (ctx.environment) {
        Environment.DEVELOPMENT, Environment.PRODUCTION -> "app-"
        Environment.TEST -> "apps-"
    }

    val domain: String = when (ctx.environment) {
        Environment.DEVELOPMENT -> "dev.cloud.sdu.dk"
        Environment.PRODUCTION -> "cloud.sdu.dk"
        Environment.TEST -> "dev.cloud.sdu.dk" // Uses different prefix
    }

    withConfigMap {
        val hostTemporaryStorage: String = when (ctx.environment) {
            Environment.DEVELOPMENT -> "/mnt/ofs"
            Environment.PRODUCTION -> "/mnt/storage/overlayfs"
            Environment.TEST -> "/mnt/ofs"
        }

        addConfig(
            "config.yaml",

            //language=yaml
            """
                app:
                  kubernetes:
                    performAuthentication: true
                    prefix: "$prefix"
                    domain: $domain
                    hostTemporaryStorage: $hostTemporaryStorage
                    toleration:
                      key: sducloud
                      value: apps
                      
            """.trimIndent()
        )
    }

    withIngress("apps") {
        resource.metadata.annotations = resource.metadata.annotations +
                mapOf("nginx.ingress.kubernetes.io/proxy-body-size" to "0")
        addRule("*.$domain", service = "app-kubernetes", port = 80)
    }
}
