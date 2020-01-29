package dk.sdu.cloud.k8

import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.util.*


private fun findServiceBundles(serviceArg: String): Collection<ResourceBundle> {
    if (serviceArg.isEmpty()) return BundleRegistry.listBundles()
    val bundle = BundleRegistry.getBundle(serviceArg)
    require(bundle != null) { "Could not find bundle: $serviceArg" }
    return listOf(bundle)
}

fun runLauncher(command: LauncherCommand, args: List<String>) {
    try {
        val scanner = Scanner(System.`in`)
        val serviceArg = args.firstOrNull() ?: ""
        val ctx = DeploymentContext(
            DefaultKubernetesClient(),
            "default",
            if (args.size <= 1) emptyList() else args.subList(1, args.size)
        )

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
                        if (resource.phase != DeploymentPhase.AD_HOC_JOB) {
                            val isUpToDate = with(resource) { ctx.isUpToDate() }
                            if (isUpToDate) println("$checkmark $resource (UP-TO-DATE)")
                            else println("$cross $resource (NOT UP-TO-DATE)")
                        }
                    }
                }
            }

            LauncherCommand.MIGRATE -> {
                findServiceBundles(serviceArg).forEach { bundle ->
                    bundle.resources.forEach { resource ->
                        if (resource.phase == DeploymentPhase.MIGRATE) {
                            val isUpToDate = with(resource) { ctx.isUpToDate() }
                            if (isUpToDate) {
                                println("$checkmark️ $resource: Already up-to-date.")
                            } else {
                                if (confirm("$question $resource: Migrate now?")) {
                                    with(resource) { ctx.create() }
                                }
                            }
                        }
                    }
                }
            }

            LauncherCommand.DEPLOY -> {
                findServiceBundles(serviceArg).forEach { bundle ->
                    bundle.resources.forEach { resource ->
                        if (resource.phase == DeploymentPhase.DEPLOY) {
                            val isUpToDate = with(resource) { ctx.isUpToDate() }
                            if (isUpToDate) {
                                println("$checkmark $resource: Already up-to-date.")
                            } else {
                                if (confirm("$question $resource: Deploy now?")) {
                                    with(resource) { ctx.create() }
                                }
                            }
                        }
                    }
                }
            }

            LauncherCommand.AD_HOC_JOB -> {
                val jobs = findServiceBundles(serviceArg).flatMap { bundle ->
                    bundle.resources.filter { it.phase == DeploymentPhase.AD_HOC_JOB }
                }

                if (jobs.isEmpty()) {
                    println("Found no ad hoc jobs for '$serviceArg'")
                } else {
                    val job: KubernetesResource = run {
                        if (jobs.size == 1) {
                            jobs.single()
                        } else {
                            println("Found the following jobs:")
                            jobs.forEachIndexed { index, resource ->
                                println("${index + 1}) $resource")
                            }
                            while (true) {
                                print("$question Select a job to run: ")
                                val selectedJob = jobs.getOrNull((scanner.nextLine().toIntOrNull() ?: -1) - 1)
                                if (selectedJob != null) {
                                    return@run selectedJob
                                }
                            }

                            @Suppress("UNREACHABLE_CODE")
                            throw IllegalStateException("Unreachable code was reached")
                        }
                    }

                    with(job) { ctx.create() }
                    println("$checkmark $job")
                }
            }
        }
    } catch (ex: Throwable) {
        ex.printStackTrace()
    }
}
