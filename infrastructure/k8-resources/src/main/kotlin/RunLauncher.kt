package dk.sdu.cloud.k8

import java.util.*

private fun findServiceBundles(serviceArg: String): Collection<ResourceBundle> {
    if (serviceArg.isEmpty()) return BundleRegistry.listBundles().map { it.first }
    val bundle = BundleRegistry.getBundle(serviceArg)
    require(bundle != null) { "Could not find bundle: $serviceArg" }
    return listOf(bundle.first)
}

fun runLauncher(
    command: LauncherCommand,
    args: List<String>,
    skipUpToDateCheck: Boolean,
    forceYes: Boolean,
    ctx: DeploymentContext
) {
    try {
        val checkmark = "✅ "
        val question = "❓ "
        val cross = "❌ "

        val scanner = Scanner(System.`in`)
        val serviceArg = args.firstOrNull() ?: ""

        BundleRegistry.listBundles().forEach { (bundle, init) ->
            init(bundle, ctx)
        }

        fun confirm(message: String): Boolean {
            while (true) {
                print("$message [Y/n] ")
                if (forceYes) {
                    println("Y")
                    return true
                }
                when (scanner.nextLine()) {
                    "", "y", "Y" -> return true
                    "n", "N" -> return false
                    else -> {
                        println("Please write y/n")
                    }
                }
            }
        }

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
                            val isUpToDate = !skipUpToDateCheck && with(resource) { ctx.isUpToDate() }
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
                            val isUpToDate = !skipUpToDateCheck && with(resource) { ctx.isUpToDate() }
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

        ctx.client.close()
    } catch (ex: Throwable) {
        ex.printStackTrace()
    }
}
