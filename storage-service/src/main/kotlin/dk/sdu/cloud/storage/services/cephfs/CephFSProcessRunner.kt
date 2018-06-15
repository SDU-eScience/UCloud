package dk.sdu.cloud.storage.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.service.transferTo
import dk.sdu.cloud.storage.util.BashEscaper
import dk.sdu.cloud.storage.util.BoundaryContainedStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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

    private val interpreter: Process = run {
        val unixUser = cloudToCephFsDao.findUnixUser(user) ?: throw IllegalStateException("Could not find user")
        val prefix = if (isDevelopment) emptyList() else listOf("sudo", "-u", unixUser)
        val command = listOf(
            "/Users/dthrane/work/sdu-cloud/storage-service/native/cmake-build-release/ceph-interpreter",
            String(clientBoundary, Charsets.UTF_8),
            String(serverBoundary, Charsets.UTF_8)
        )

        ProcessBuilder().apply { command(prefix + command) }.start()
    }

    private val wrappedStdout =
        BoundaryContainedStream(serverBoundary, interpreter.inputStream)
    private val wrappedStderr =
        BoundaryContainedStream(serverBoundary, interpreter.errorStream)

    private val outputStream =
        StreamingOutputStream(interpreter.outputStream, onClose = {
            log.debug("Writing the client boundary")
            it.write(clientBoundary)
            it.flush()
        })

    fun runCommand(
        vararg command: String,
        writer: (OutputStream) -> Unit = {},
        consumer: (InputStream) -> Unit = {}
    ) {
        log.debug("Interpreter alive? ${interpreter.isAlive}")

        if (!interpreter.isAlive) TODO("Handle interpreter not being alive")

        outputStream.write((command.joinToString("\n") + "\n").toByteArray())
        outputStream.flush()
        outputStream.use {
            writer(GuardedOutputStream(it))
        }

        consumer(wrappedStdout)

        wrappedStdout.discardAndReset()
        wrappedStderr.discardAndReset()
    }

    fun close() {
        interpreter.outputStream.close()
        interpreter.destroy()
    }

    companion object {
        private val log = LoggerFactory.getLogger(StreamingProcessRunner::class.java)
    }
}

fun main(args: Array<String>) {
    val runner = StreamingProcessRunner(CloudToCephFsDao(true), true, "jonas@hinchely.dk")
    runner.runCommand("list-directory", "/tmp/", "0")
    runner.runCommand("list-directory", "/tmp/", "0")

    var sentBytes = 0L
    var sentChecksum = 0L

    var receivedBytes = 0L
    var receivedChecksum = 0L

    var controlBytes = 0L
    var controlChecksum = 0L

    runner.runCommand(
        "write", "/tmp/my-file",
        writer = { out ->
            (0..255).forEach { a ->
                (0..255).forEach { b ->
                    out.write(a)
                    out.write(b)
                    sentChecksum += a.toByte()
                    sentChecksum += b.toByte()

                    sentBytes += 2
                }
            }
        }
    )

    val debugOut = File("/tmp/debug").outputStream()
    debugOut.use {
        val buffer = ByteArray(8 * 1024)
        runner.runCommand("read", "/tmp/my-file", consumer = {
            var foundNewline = false
            var byte = it.read(buffer)
            while (byte != -1) {
                debugOut.write(buffer, 0, byte)
                for (i in 0 until byte) {
                    if (foundNewline) {
                        receivedChecksum += buffer[i]
                    }

                    if (buffer[i] == '\n'.toByte()) {
                        foundNewline = true
                    }
                }

                receivedBytes += byte
                byte = it.read(buffer)
            }
//            }
        })
    }

    File("/tmp/my-file").inputStream().use { ins ->
        var byte = ins.read()
        while (byte != -1) {
            controlChecksum += byte.toByte()
            byte = ins.read()
            controlBytes++
        }
    }


    println()
    println("Sent bytes: $sentBytes, checksum: $sentChecksum")
    println("Received bytes: $receivedBytes, checksum: $receivedChecksum")
    println("Control bytes: $controlBytes, checksum: $controlChecksum")

    File("/tmp/1g_copy").delete()
    runner.runCommand("write", "/tmp/1g_copy", writer = {
        File("/tmp/1g").inputStream().transferTo(it)
    })

    File("/tmp/1g_copy").delete()
    File("/tmp/1g").inputStream().transferTo(File("/tmp/1g_copy").outputStream())
}


class StreamingOutputStream(private val delegate: OutputStream, private val onClose: (OutputStream) -> Unit) :
    OutputStream() {
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
        onClose(this)
    }
}
