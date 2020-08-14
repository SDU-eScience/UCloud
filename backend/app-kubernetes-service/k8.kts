package dk.sdu.cloud.k8

//DEPS dk.sdu.cloud:k8-resources:0.1.2

bundle { ctx ->
    name = "app-kubernetes"
    version = "0.19.0"

    val prefix: String = config("prefix", "Application name prefix (e.g. 'app-')", "app-")
    val domain: String = config("domain", "Application domain (e.g. 'cloud.sdu.dk')")
    val internalEgressWhiteList: List<String> = config(
        "internalEgressWhitelist",
        "Internal sites to whitelist",
        emptyList()
    )

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

    listOf("", "-dev").forEach { suffix ->
        val networkPolicyPodSelector = mapOf("role" to "sducloud-app$suffix")
        withNetworkPolicy("app-policy$suffix", version = "3") {
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
                    )
                ) + internalEgressWhiteList.map {
                    allowEgressTo(listOf(EgressToPolicy(it)))
                }
            }
        }

        withNetworkPolicy("app-allow-proxy$suffix", version = "3") {
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
    }

    withClusterServiceAccount {
        addRule(
            apiGroups = listOf(""),
            resources = listOf("pods", "pods/log", "pods/portforward", "pods/exec", "services"),
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

    withConfigMap {
        addConfig(
            "config.yaml",

            //language=yaml
            """
                app:
                  kubernetes:
                    performAuthentication: true
                    prefix: "$prefix"
                    domain: $domain
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
