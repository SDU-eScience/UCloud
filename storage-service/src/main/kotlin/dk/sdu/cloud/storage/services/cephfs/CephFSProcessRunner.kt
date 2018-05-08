package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.util.BashEscaper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream

private inline fun Logger.debug(closure: () -> String) {
    if (isDebugEnabled) debug(closure())
}

data class InMemoryProcessResultAsString(val status: Int, val stdout: String, val stderr: String)

interface IProcess {
    val inputStream: InputStream
    val errorStream: InputStream
    val outputStream: OutputStream
    fun waitFor(): Int
}

class JavaProcess(private val realProcess: Process) : IProcess {
    override val inputStream: InputStream
        get() = realProcess.inputStream
    override val errorStream: InputStream
        get() = realProcess.errorStream
    override val outputStream: OutputStream
        get() = realProcess.outputStream

    override fun waitFor(): Int {
        return realProcess.waitFor()
    }
}

typealias ProcessRunnerFactory = (user: String) -> ProcessRunner

interface ProcessRunner {
    val user: String

    fun run(command: List<String>, directory: String? = null): IProcess

    fun runWithResultAsInMemoryString(
        command: List<String>,
        directory: String? = null
    ): InMemoryProcessResultAsString {
        val process = run(command, directory)

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val status = process.waitFor()
        log.debug { "status: $status" }
        log.debug { "stdout: $stdout" }
        log.debug { "stderr: $stderr" }

        return InMemoryProcessResultAsString(status, stdout, stderr)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProcessRunner::class.java)
    }
}

fun SimpleCephFSProcessRunner(cloudToCephFsDao: CloudToCephFsDao, isDevelopment: Boolean): ProcessRunnerFactory {
    return { user: String ->
        CephFSProcessRunner(cloudToCephFsDao, isDevelopment, user)
    }
}

class CephFSProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean,
    override val user: String
): ProcessRunner {
    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    override fun run(command: List<String>, directory: String?): IProcess {
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
        }.start().let { JavaProcess(it) }
    }

    companion object {
        private val log =
            LoggerFactory.getLogger(CephFSProcessRunner::class.java)
    }
}