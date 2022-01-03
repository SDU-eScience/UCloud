package dk.sdu.cloud.k8

import io.fabric8.kubernetes.client.Config
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import java.io.*
import javax.script.ScriptEngineManager
import kotlin.system.exitProcess

enum class LauncherCommand(val cmd: String) {
    UP_TO_DATE("status"),
    MIGRATE("migrate"),
    DEPLOY("deploy"),
    AD_HOC_JOB("job")
}

fun main(args: Array<String>) {
    var directory = "."
    val freeformArgs = ArrayList<String>()
    val additionalFiles = ArrayList<String>()
    val importBuilder = StringBuilder()
    val outputScript = StringBuilder()
    var forceYes = false
    var environment = "development"
    val imports = HashSet<String>()

    val engine = ScriptEngineManager().getEngineByExtension("kts")!!
    var skipUpToDateCheck = false

    run {
        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "--directory" -> {
                    directory = args.getOrNull(i + 1) ?: directory
                    i++
                }

                "--file" -> {
                    val file = args.getOrNull(i + 1)
                    if (file != null) additionalFiles.add(file)
                    i++
                }

                "--yes" -> {
                    forceYes = true
                }

                "--force" -> {
                    skipUpToDateCheck = true
                }

                "--env" -> {
                    val envString = args.getOrNull(i + 1)
                    i++

                    environment = envString ?: throw IllegalStateException("No environment passed")
                }

                else -> {
                    freeformArgs.add(arg)
                }
            }

            i++
        }
    }

    fun File.needsToBeLoaded(): Boolean {
        return name == "k8.kts" && isFile
    }

    val allBundles = ArrayList<File>()
    File(directory).list()
        ?.filter {
            it.endsWith("-service") || it == "frontend-web" || it == "k8.kts" || it == "infrastructure"
        }
        ?.forEach { folder ->
            val thisFile = File(directory, folder)
            if (thisFile.needsToBeLoaded()) {
                allBundles.add(thisFile)
            } else {
                thisFile.listFiles()?.filter { it.needsToBeLoaded() }?.forEach { allBundles.add(it) }
            }
        }

    val repositoryRoot = if (File(directory).list()?.any { it.endsWith("-service") } == true) {
        File(directory)
    } else {
        val parentFile = File(directory).absoluteFile.normalize().parentFile
        val backendAttempt = File(parentFile, "backend")
        when {
            parentFile.list()?.any { it.endsWith("-service") } == true -> {
                parentFile
            }
            backendAttempt.list()?.any { it.endsWith("-service") } == true -> {
                backendAttempt
            }
            else -> {
                throw IllegalStateException("Could not find repository root")
            }
        }
    }

    additionalFiles.forEach { allBundles.add(File(it)) }

    importBuilder.appendln("package dk.sdu.cloud.k8")

    allBundles.forEach { file ->
        if (!file.exists()) {
            System.err.println("Could not find: $file")
        } else {
            file.useLines { lines ->
                lines.forEach { line ->
                    when {
                        line.startsWith("package") ||
                                line.startsWith("//DEPS dk.sdu.cloud:k8-resources") -> {
                            // Do nothing
                        }

                        line.startsWith("import") -> {
                            if (line.trim() !in imports) {
                                importBuilder.appendln(line)
                                imports.add(line.trim())
                            }
                        }

                        else -> {
                            outputScript.appendln(line)
                        }
                    }
                }
            }
        }
    }

    val command = freeformArgs.firstOrNull() ?: "status"
    val remainingArgs = freeformArgs.subList(1, freeformArgs.size)

    val launcherCommand = LauncherCommand.values().find { it.cmd == command }
    require(launcherCommand != null) { "No such command '$command'" }

    System.err.println("k8.kts files are being compiled now...")

    val kubeConfig = File(System.getProperty("user.home"), ".kube/config").readText()
    engine.eval(importBuilder.toString() + "\n" + outputScript.toString())
    val ctx = DeploymentContext(
        DefaultKubernetesClient(Config.fromKubeconfig(environment.toLowerCase(), kubeConfig, null)),
        "default",
        if (remainingArgs.size <= 1) emptyList() else remainingArgs.subList(1, args.size),
        environment,
        repositoryRoot
    )

    Configuration.runBlocks(ctx)
    runLauncher(launcherCommand, remainingArgs, skipUpToDateCheck, forceYes, ctx)
}
