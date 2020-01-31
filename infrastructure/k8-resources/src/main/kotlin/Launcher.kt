package dk.sdu.cloud.k8

import java.io.*
import javax.script.ScriptEngineManager

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
    val outputScript = StringBuilder()

    val engine = ScriptEngineManager().getEngineByExtension("kts")!!
    var skipUpToDateCheck = false

    outputScript.appendln("package dk.sdu.cloud.k8")

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
                }

                "--force" -> {
                    skipUpToDateCheck = true
                }

                else -> {
                    freeformArgs.add(arg)
                }
            }

            i++
        }
    }

    val allBundles = ArrayList<File>()
    File(directory).list()
        ?.filter { it.endsWith("-service") || it == "frontend-web" }
        ?.forEach { folder ->
            val k8 = File(File(directory, folder), "k8.kts")
            allBundles.add(k8)
        }

    additionalFiles.forEach { allBundles.add(File(it)) }

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

    System.err.println("Dynamic scripts are being compiled...")
    engine.eval(outputScript.toString())
    runLauncher(launcherCommand, remainingArgs, skipUpToDateCheck)
}
