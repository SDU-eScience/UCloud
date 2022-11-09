package dk.sdu.cloud

import java.io.File

fun findCompose(): DockerCompose {
    val dockerExe = startProcessAndCollectToString(listOf("/usr/bin/which", "docker")).stdout.trim().takeIf { it.isNotEmpty() }
    val dockerComposeExe = startProcessAndCollectToString(listOf("/usr/bin/which", "docker-compose")).stdout.trim().takeIf { it.isNotEmpty() }

    val hasPluginStyle = runCatching {
        startProcessAndCollectToString(listOf(dockerExe!!, "compose", "version"))
    }.getOrNull()?.takeIf { it.stdout.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasPluginStyle) return DockerCompose.Plugin

    val hasClassicStyle = runCatching {
        startProcessAndCollectToString(listOf(dockerComposeExe!!, "version"))
    }.getOrNull()?.takeIf { it.stdout.startsWith("Docker Compose", ignoreCase = true) } != null

    if (hasClassicStyle) return DockerCompose.Classic

    error("Could not find docker compose!")
}

data class ExecutableCommand(
    val args: List<String>,
    val workingDir: File? = null,
    val postProcessor: (result: ProcessResultText) -> String = { it.stdout },
) {
    fun executeToText(): String {
        return postProcessor(startProcessAndCollectToString(args, workingDir = workingDir))
    }
}

sealed class DockerCompose {
    abstract fun up(directory: File): ExecutableCommand
    abstract fun down(directory: File): ExecutableCommand
    abstract fun ps(directory: File): ExecutableCommand
    abstract fun logs(directory: File, container: String): ExecutableCommand

    object Classic : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker-compose", "up"), directory)

        override fun down(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker-compose", "down"), directory)

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker-compose", "ps"), directory)

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf("docker-compose", "logs", container), directory)
    }

    object Plugin : DockerCompose() {
        override fun up(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker", "compose", "--project-directory", directory.absolutePath, "up"), directory)

        override fun down(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker", "compose", "--project-directory", directory.absolutePath, "down"), directory)

        override fun ps(directory: File): ExecutableCommand =
            ExecutableCommand(listOf("docker", "compose", "--project-directory", directory.absolutePath, "ps"), directory)

        override fun logs(directory: File, container: String): ExecutableCommand =
            ExecutableCommand(listOf("docker", "compose", "--project-directory", directory.absolutePath, "logs", container), directory)
    }
}
