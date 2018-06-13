package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.*
import java.util.*

private inline fun Logger.debug(closure: () -> String) {
    if (isDebugEnabled) debug(closure())
}

data class InMemoryProcessResultAsString(val status: Int, val stdout: String, val stderr: String)

interface IProcess : Closeable {
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

    override fun close() {
        realProcess.destroy()
    }
}

typealias ProcessRunnerFactory = (user: String) -> ProcessRunner

interface ProcessRunner : Closeable {
    val user: String

    fun run(command: List<String>, directory: String? = null, noEscape: Boolean = false): IProcess

    fun runWithResultAsInMemoryString(
        command: List<String>,
        directory: String? = null
    ): InMemoryProcessResultAsString {
        val process = run(command, directory)

        process.outputStream.close()
        val stdout = process.inputStream.bufferedReader().readText()
        log.debug { "stdout: $stdout" }
        val stderr = process.errorStream.bufferedReader().readText()
        log.debug { "stderr: $stderr" }
        val status = process.waitFor()
        log.debug { "status: $status" }

        return InMemoryProcessResultAsString(status, stdout, stderr)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ProcessRunner::class.java)
    }
}

@Suppress("FunctionName")
fun SimpleCephFSProcessRunnerFactory(cloudToCephFsDao: CloudToCephFsDao, isDevelopment: Boolean): ProcessRunnerFactory {
    return { user: String ->
        CephFSProcessRunner(cloudToCephFsDao, isDevelopment, user)
    }
}

class CephFSProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean,
    override val user: String
) : ProcessRunner {
    private fun asUser(cloudUser: String): List<String> {
        val user = cloudToCephFsDao.findUnixUser(cloudUser) ?: throw IllegalStateException("Could not find user")
        return if (!isDevelopment) listOf("sudo", "-u", user) else emptyList()
    }

    override fun run(command: List<String>, directory: String?, noEscape: Boolean): IProcess {
        return ProcessBuilder().apply {
            val prefix = asUser(user)

            val bashCommand = if (directory == null) {
                command.joinToString(" ") {
                    if (noEscape) it
                    else BashEscaper.safeBashArgument(it)
                }
            } else {
                // TODO We need to ensure directory exists. We cannot do this with File(directory)
                "cd ${BashEscaper.safeBashArgument(directory)} && " +
                        command.joinToString(" ") {
                            if (noEscape) it
                            else BashEscaper.safeBashArgument(it)
                        }
            }
            log.debug("Running command (user=$user): $bashCommand [$command]")

            val wrappedCommand = listOf("bash", "-c", bashCommand)
            command(prefix + wrappedCommand)
        }.start().let { JavaProcess(it) }
    }

    override fun close() {
        // No-op
    }

    companion object {
        private val log =
            LoggerFactory.getLogger(CephFSProcessRunner::class.java)
    }
}

class StreamingProcessRunner(
    private val cloudToCephFsDao: CloudToCephFsDao,
    private val isDevelopment: Boolean,
    val user: String
) {
    private val clientBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
    private val serverBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)

    private val bashProcess = run {
        val unixUser = cloudToCephFsDao.findUnixUser(user) ?: throw IllegalStateException("Could not find user")
        val prefix = if (isDevelopment) emptyList() else listOf("sudo", "-u", unixUser)
        val command = listOf(
            "ceph-interpreter",
            String(clientBoundary, Charsets.UTF_8),
            String(serverBoundary, Charsets.UTF_8)
        )

        ProcessBuilder().apply { command(prefix + command) }.start()
    }

    fun debug() {
        bashProcess.outputStream.close()
        bashProcess.waitFor()
        println(bashProcess.inputStream.bufferedReader().readText())
        println(bashProcess.errorStream.bufferedReader().readText())
    }

    private fun InputStream.withDebugging(timestamp: Long, name: String): InputStream =
        if (isDevelopment) DebugInputStream(this, FileOutputStream(File(debugFolder, "$timestamp-$name")))
        else this

    fun run(vararg command: String): BoundaryContainedProcess {
        log.debug("Bash process alive? ${bashProcess.isAlive}")
        val out = bashProcess.outputStream
        out.apply {
            write(command.joinToString(" ").toByteArray())

            // Always flush at end of command
            flush()
        }

        val startTimestamp = System.currentTimeMillis()

        val wrappedStdout =
            BoundaryContainedStream(serverBoundary, bashProcess.inputStream.withDebugging(startTimestamp, "stdout.log"))
        val wrappedStderr =
            BoundaryContainedStream(serverBoundary, bashProcess.errorStream.withDebugging(startTimestamp, "stderr.log"))

        val outputStream = StreamingOutputStream(out, onClose = {
            out.write(clientBoundary)
            out.flush()
        })

        return BoundaryContainedProcess(
            wrappedStdout,
            wrappedStderr,
            outputStream
        )
    }

    fun close() {
        bashProcess.outputStream.close()
        bashProcess.destroy()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StreamingProcessRunner::class.java)

        private val debugFolder = File("debug").also {
            it.mkdirs()
        }
    }
}

class BoundaryContainedProcess(
    val inputStream: BoundaryContainedStream,
    val errorStream: BoundaryContainedStream,
    val outputStream: OutputStream
) {
    fun waitFor() {
        inputStream.discardAll()
        errorStream.discardAll()
        outputStream.close()
        // TODO
    }

    fun close() {
        inputStream.discardAll()
        errorStream.discardAll()
        outputStream.close()
    }
}

class DebugInputStream(private val delegate: InputStream, private val debugOutput: OutputStream) : InputStream() {
    override fun skip(n: Long): Long {
        return delegate.skip(n)
    }

    override fun available(): Int {
        return delegate.available()
    }

    override fun reset() {
        delegate.reset()
    }

    override fun close() {
        delegate.close()
    }

    override fun mark(readlimit: Int) {
        delegate.mark(readlimit)
    }

    override fun markSupported(): Boolean {
        return delegate.markSupported()
    }

    override fun read(): Int {
        return delegate.read().also { if (it != -1) debugOutput.write(it) }
    }

    override fun read(b: ByteArray?): Int {
        return delegate.read(b).also { if (it != -1) debugOutput.write(b) }
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        return delegate.read(b, off, len).also { if (it != -1) debugOutput.write(b, off, it) }
    }
}

class StreamingOutputStream(private val delegate: OutputStream, private val onClose: () -> Unit) : OutputStream() {
    override fun write(b: Int) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?) {
        delegate.write(b)
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        delegate.write(b, off, len)
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        onClose()
    }
}
