package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private inline fun Logger.debug(closure: () -> String) {
    if (isDebugEnabled) debug(closure())
}

data class InMemoryProcessResultAsString(val status: Int, val stdout: String, val stderr: String)

class CephFSProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean
) {
    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    fun runAsUser(user: String, command: List<String>, directory: String? = null): Process {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            } else {
                "cd ${BashEscaper.safeBashArgument(directory)} ; " +
                        command.joinToString(" ") { BashEscaper.safeBashArgument(it) }
            }

            val wrappedCommand = listOf("bash", "-c", bashCommand)
            log.debug(wrappedCommand.toString())
            command(prefix + wrappedCommand)
        }.start()
    }

    fun runAsUserWithResultAsInMemoryString(
        user: String,
        command: List<String>,
        directory: String? = null
    ): InMemoryProcessResultAsString {
        val process = runAsUser(user, command, directory)

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val status = process.waitFor()
        log.debug { "status: $status" }
        log.debug { "stdout: $stdout" }
        log.debug { "stderr: $stderr" }

        return InMemoryProcessResultAsString(status, stdout, stderr)
    }

    companion object {
        private val log =
            LoggerFactory.getLogger(CephFSProcessRunner::class.java)
    }
}