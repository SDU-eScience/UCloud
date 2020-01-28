package dk.sdu.cloud.k8

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import java.util.*

private fun findServiceBundles(serviceArg: String): Collection<ResourceBundle> {
    if (serviceArg.isEmpty()) return BundleRegistry.listBundles()
    val bundle = BundleRegistry.getBundle(serviceArg)
    require(bundle != null) { "Could not find bundle: $serviceArg" }
    return listOf(bundle)
}

fun runLauncher(command: LauncherCommand, serviceArg: String) {
    try {
        val client: KubernetesClient = DefaultKubernetesClient()
        val scanner = Scanner(System.`in`)
        val namespace = "default"

        fun confirm(message: String): Boolean {
            while (true) {
                print("$message [Y/n] ")
                when (scanner.nextLine()) {
                    "", "y", "Y" -> return true
                    "n", "N" -> return false
                    else -> {
                        println("Please write y/n")
                    }
                }
            }
        }

        val checkmark = "✔️  "
        val question = "❓  "
        val cross = "❌  "

        when (command) {
            LauncherCommand.UP_TO_DATE -> {
                findServiceBundles(serviceArg).forEach { bundle ->
                    bundle.resources.forEach { resource ->
                        val isUpToDate = resource.isUpToDate(client, namespace)
                        if (isUpToDate) println("$checkmark $resource (UP-TO-DATE)")
                        else println("$cross $resource (NOT UP-TO-DATE)")
                    }
                }
            }

            LauncherCommand.MIGRATE -> {
                findServiceBundles(serviceArg).forEach { bundle ->
                    bundle.resources.forEach { resource ->
                        if (resource.isMigration) {
                            val isUpToDate = resource.isUpToDate(client, namespace)
                            if (isUpToDate) {
                                println("$checkmark️ $resource: Already up-to-date.")
                            } else {
                                if (confirm("$question $resource: Migrate now?")) {
                                    resource.create(client, namespace)
                                }
                            }
                        }
                    }
                }
            }

            LauncherCommand.DEPLOY -> {
                findServiceBundles(serviceArg).forEach { bundle ->
                    bundle.resources.forEach { resource ->
                        if (!resource.isMigration) {
                            val isUpToDate = resource.isUpToDate(client, namespace)
                            if (isUpToDate) {
                                println("$checkmark $resource: Already up-to-date.")
                            } else {
                                if (confirm("$question $resource: Deploy now?")) {
                                    resource.create(client, namespace)
                                }
                            }
                        }
                    }
                }
            }
        }
    } catch (ex: Throwable) {
        ex.printStackTrace()
    }
}
