package dk.sdu.cloud

import java.io.File
import kotlin.system.exitProcess

fun findDocker(): String {
    return startProcessAndCollectToString(listOf("/usr/bin/which", "docker")).stdout.trim().takeIf { it.isNotEmpty() }
        ?: error("Could not find Docker!")
}

fun findCompose(): DockerCompose {
    val dockerExe = findDocker()
    val dockerComposeExe = startProcessAndCollectToString(listOf("/usr/bin/which", "docker-compose")).stdout.trim().takeIf { it.isNotEmpty() }

    val hasPluginStyle = runCatching {
        startProcessAndCollectToString(listOf(dockerExe, "compose", "version"))
    }.getOrNull()?.takeIf { it.stdout.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasPluginStyle) return DockerCompose.Plugin(dockerExe)

    val hasClassicStyle = runCatching {
        startProcessAndCollectToString(listOf(dockerComposeExe!!, "version"))
    }.getOrNull()?.takeIf { it.stdout.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasClassicStyle) return DockerCompose.Classic(dockerComposeExe!!)

    error("Could not find docker compose!")
}

data class ExecutableCommand(
    val args: List<String>,
    val workingDir: File? = null,
    val postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    val allowFailure: Boolean = false,
) {
    fun executeToText(): String? {
        val result = startProcessAndCollectToString(args, workingDir = workingDir)
        if (result.statusCode != 0) {
            if (allowFailure) return null

            println("Command failed!")
            println("Command: " + args.joinToString(" ") { "'$it'" })
            println("Directory: $workingDir")
            println("Exit code: ${result.statusCode}")
            println("Stdout: ${result.stdout}")
            println("Stderr: ${result.stderr}")
            exitProcess(result.statusCode)
        }
        return postProcessor(result)
    }
}

sealed class DockerCompose {
    abstract fun up(directory: File): ExecutableCommand
    abstract fun down(directory: File): ExecutableCommand
    abstract fun ps(directory: File): ExecutableCommand
    abstract fun logs(directory: File, container: String): ExecutableCommand

    class Classic(val exe: String) : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "up"), directory)

        override fun down(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "down"), directory)

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "ps"), directory, allowFailure = true)

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf(exe, "logs", container), directory)
    }

    class Plugin(val exe: String) : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "compose", "--project-directory", directory.absolutePath, "up"), directory)

        override fun down(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "compose", "--project-directory", directory.absolutePath, "down"), directory)

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(listOf(exe, "compose", "--project-directory", directory.absolutePath, "ps"), directory, allowFailure = true)

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf(exe, "compose", "--project-directory", directory.absolutePath, "logs", container), directory)
    }
}
