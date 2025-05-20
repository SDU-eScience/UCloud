package dk.sdu.cloud.plugins.compute.ucloud

import dk.sdu.cloud.config.ConfigSchema
import dk.sdu.cloud.dbConnection
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.loadedConfig
import dk.sdu.cloud.sql.useAndInvoke
import dk.sdu.cloud.sql.withSession
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.PrintWriter

suspend fun runIm2Migration(args: Array<String>) {
    val outputDir = File("/tmp/provider-${loadedConfig.core.providerId}").also { it.mkdirs() }
    val rawConfig = loadedConfig.rawPluginConfig.jobs!!.values.single()
    val pluginConfig = rawConfig as ConfigSchema.Plugins.Jobs.UCloud
    val namespace = pluginConfig.kubernetes.namespace
    val dryRun = !args.contains("--dry=false")

    val k8sClient = KubernetesClient(
        if (pluginConfig.kubernetes.serviceUrl != null) {
            KubernetesConfigurationSource.InClusterConfiguration(pluginConfig.kubernetes.serviceUrl)
        } else if (pluginConfig.kubernetes.configPath != null) {
            KubernetesConfigurationSource.KubeConfigFile(pluginConfig.kubernetes.configPath, null)
        } else {
            KubernetesConfigurationSource.Auto
        }
    )

    run {
        val allPolicies = k8sClient.listResources(
            NetworkPolicy.serializer(),
            KubernetesResources.networkPolicies.withNamespace(namespace)
        )
        val policiesToDelete = HashSet<String>()
        for (policy in allPolicies.items) {
            val name = policy.metadata?.name ?: continue
            if (name.startsWith("policy-")) {
                policiesToDelete.add(name)
            }
        }

        if (!dryRun) {
            for (policy in policiesToDelete) {
                k8sClient.deleteResource(KubernetesResources.networkPolicies.withNameAndNamespace(policy, namespace))
            }
        }
        val fw = PrintWriter(File(outputDir, "deleted_policies.txt"))
        for (name in policiesToDelete) {
            fw.println(name)
        }
        fw.close()
    }

    run {
        val allPolicies = k8sClient.listResources(
            Service.serializer(),
            KubernetesResources.services.withNamespace(namespace)
        )
        val toDelete = HashSet<String>()
        for (item in allPolicies.items) {
            val name = item.metadata?.name ?: continue
            if (name.startsWith("j-")) {
                toDelete.add(name)
            }
        }

        if (!dryRun) {
            for (policy in toDelete) {
                k8sClient.deleteResource(KubernetesResources.services.withNameAndNamespace(policy, namespace))
            }
        }
        val fw = PrintWriter(File(outputDir, "deleted_services.txt"))
        for (name in toDelete) {
            fw.println(name)
        }
        fw.close()
    }

    run {
        val allPods = k8sClient.listResources(Pod.serializer(), KubernetesResources.pod.withNamespace(namespace))
        val podsToDelete = HashSet<String>()
        for (pod in allPods.items) {
            if (pod.metadata?.name?.startsWith("j-") == true) {
                podsToDelete.add(pod.metadata!!.name!!)
            }
        }
        if (!dryRun) {
            for (pod in podsToDelete) {
                k8sClient.deleteResource(KubernetesResources.pod.withNameAndNamespace(pod, namespace))
            }
        }
        val fw = PrintWriter(File(outputDir, "deleted_pods.txt"))
        for (podName in podsToDelete) {
            fw.println(podName)
        }
        fw.close()
    }

    run {
        dbConnection.withSession { session ->
            val licenseFile = PrintWriter(File(outputDir, "licenses.jsonl"))
            session.prepareStatement(
                """
                    select name, address, port, license from generic_license_servers;
                """
            ).useAndInvoke(
                prepare = {},
                readRow = { row ->
                    val name = row.getString(0)!!
                    val address = row.getString(1)
                    val port = row.getInt(2)
                    val license = row.getString(3)

                    licenseFile.println(defaultMapper.encodeToString(arrayListOf(name, address, port?.toString(), license)))
                }
            )
            licenseFile.close()

            val networkFile = PrintWriter(File(outputDir, "ips.jsonl"))
            session.prepareStatement(
                """
                    select external_cidr, internal_cidr from ucloud_compute_network_ip_pool;
                """
            ).useAndInvoke(
                prepare = {},
                readRow = { row ->
                    val externalCidr = row.getString(0)!!
                    val internalCidr = row.getString(1)!!

                    networkFile.println(defaultMapper.encodeToString(arrayListOf(externalCidr, internalCidr)))
                }
            )
            networkFile.close()
        }
    }

    println("Data migration complete")
    while(true) {
        delay(1000)
    }
}
