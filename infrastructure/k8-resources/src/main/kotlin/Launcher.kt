package dk.sdu.cloud.k8

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Files

enum class LauncherCommand(val cmd: String) {
    UP_TO_DATE("status"),
    MIGRATE("migrate"),
    DEPLOY("deploy")
}

fun main(args: Array<String>) {
    var directory = "."
    val freeformArgs = ArrayList<String>()
    val additionalFiles = ArrayList<String>()
    val outputScriptFile = Files.createTempFile("k8", ".kts").toFile()
    val outputScript = PrintWriter(BufferedOutputStream(FileOutputStream(outputScriptFile)))

    outputScript.println("package dk.sdu.cloud.k8")
    // TODO We need to add a requirement for running this with custom maven repository
    // Credentials can be read via environment variables (bootstrap using gradle properties)
    outputScript.println("//DEPS dk.sdu.cloud:k8-resources:0.1.0 org.slf4j:slf4j-simple:1.7.25")

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

                else -> {
                    freeformArgs.add(arg)
                }
            }

            i++
        }
    }

    val allBundles = ArrayList<File>()
    File(directory).list()
        ?.filter { it.endsWith("-service") }
        ?.forEach { folder ->
            val k8 = File(File(directory, folder), "k8.kts")
            allBundles.add(k8)
        }

    additionalFiles.forEach { allBundles.add(File(it)) }

    allBundles.forEach { file ->
        if (!file.exists()) {
            System.err.println("Could not find: ${file.absolutePath}")
        }

        file.useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("package") ||
                    line.startsWith("//DEPS dk.sdu.cloud:k8-resources") -> {
                        // Do nothing
                    }

                    else -> {
                        outputScript.println(line)
                    }
                }
            }
        }
    }

    var command = "status"
    var serviceArg = ""
    when (freeformArgs.size) {
        2 -> {
            command = freeformArgs[0]
            serviceArg = freeformArgs[1]
        }

        1 -> {
            command = freeformArgs[0]
        }
    }

    val launcherCommand = LauncherCommand.values().find { it.cmd == command }

    require(launcherCommand != null) { "No such command '$command'" }
    require(!serviceArg.contains("\n"))
    require(!serviceArg.contains("\""))
    require(!command.contains("\n"))
    require(!command.contains("\""))

    outputScript.println("runLauncher(LauncherCommand.${launcherCommand.name}, \"$serviceArg\")")
    outputScript.close()

    println(outputScriptFile.absolutePath)
}
