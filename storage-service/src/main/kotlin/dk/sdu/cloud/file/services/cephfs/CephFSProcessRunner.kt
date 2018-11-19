package dk.sdu.cloud.file.services.cephfs

import dk.sdu.cloud.service.GuardedOutputStream
import dk.sdu.cloud.file.SERVICE_USER
import dk.sdu.cloud.file.services.CommandRunner
import dk.sdu.cloud.file.services.FSCommandRunnerFactory
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.util.BoundaryContainedStream
import dk.sdu.cloud.file.util.FSException
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import kotlin.NoSuchElementException
import kotlin.collections.ArrayList
import kotlin.collections.set

class CephFSCommandRunnerFactory(
    private val userDao: StorageUserDao,
    private val isDevelopment: Boolean
) : FSCommandRunnerFactory<CephFSCommandRunner>() {
    override fun invoke(user: String): CephFSCommandRunner {
        return CephFSCommandRunner(userDao, isDevelopment, user)
    }
}

@Suppress("unused")
data class ProcessRunnerAttributeKey<T>(val name: String)

class CephFSCommandRunner(
    private val cephFSUserDao: StorageUserDao,
    private val isDevelopment: Boolean,
    override val user: String
) : CommandRunner {
    private val cache: MutableMap<ProcessRunnerAttributeKey<*>, Any> = hashMapOf()

    private val clientBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
    private val serverBoundary = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)

    private val interpreter: Process = run {
        val prefix = if (isDevelopment) {
            emptyList()
        } else {
            if (user == SERVICE_USER) {
                emptyList()
            } else {
                val unixUser = cephFSUserDao.findStorageUser(user) ?: throw IllegalStateException("Could not find user")
                listOf("sudo", "-u", unixUser)
            }
        }

        val command = listOf(
            "ceph-interpreter",
            String(clientBoundary, Charsets.UTF_8),
            String(serverBoundary, Charsets.UTF_8)
        )

        ProcessBuilder().apply { command(prefix + command) }.start().also { process ->
            val bytes = ByteArray(serverBoundary.size)
            var ptr = 0

            var read = process.errorStream.read(bytes, ptr, bytes.size)
            ptr += read
            while (read > 0 && (ptr - bytes.size) > 0) {
                read = process.errorStream.read(bytes, ptr, ptr - bytes.size)
                ptr += read
            }

            if (!process.isAlive) {
                throw FSException.NotReady()
            }

            if (!bytes.contentEquals(serverBoundary)) {
                throw FSException.NotReady()
            }
        }
    }

    private val wrappedStdout =
        BoundaryContainedStream(
            serverBoundary,
            interpreter.inputStream
        )

    private val wrappedStderr =
        BoundaryContainedStream(
            serverBoundary,
            interpreter.errorStream
        )

    val stdout: InputStream = wrappedStdout
    val stderr: InputStream = wrappedStdout

    fun stdoutLineSequence(): Sequence<String> = stdout.bufferedReader().lineSequence()

    private val outputStream =
        StreamingOutputStream(interpreter.outputStream, onClose = {
            it.write(clientBoundary)
            it.flush()
        })

    fun <T : Any> store(key: ProcessRunnerAttributeKey<T>, value: T) {
        cache[key] = value
    }

    fun <T : Any> retrieve(key: ProcessRunnerAttributeKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return (cache[key] ?: throw NoSuchElementException()) as T
    }

    fun <T : Any> retrieveOrNull(key: ProcessRunnerAttributeKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return cache[key] as T?
    }

    fun invalidate(key: ProcessRunnerAttributeKey<*>) {
        cache.remove(key)
    }

    fun <T> runCommand(
        command: InterpreterCommand,
        vararg args: String,
        writer: (OutputStream) -> Unit = {},
        consumer: (CephFSCommandRunner) -> T
    ): T {
        log.debug("Running command: $command ${args.joinToString(" ")}")

        if (!interpreter.isAlive) throw IllegalStateException("Unexpected EOF")

        val serializedCommand = StringBuilder().apply {
            append(command.command)
            append("\n")
            if (args.isNotEmpty()) {
                append(args.joinToString("\n"))
                append("\n")
            }
        }.toString().toByteArray()
        outputStream.write(serializedCommand)
        outputStream.flush()
        outputStream.use {
            writer(GuardedOutputStream(it))
        }

        return try {
            consumer(this)
        } finally {
            if (!interpreter.isAlive) throw IllegalStateException("Unexpected EOF (after consumer)")

            wrappedStdout.discardAndReset()

            if (log.isDebugEnabled) {
                wrappedStderr.bufferedReader().readText().lines().forEach { log.debug(it) }
            }
            wrappedStderr.discardAndReset()
        }
    }

    fun clearBytes(numberOfBytes: Long) {
        log.debug("Clearing $numberOfBytes from stdout")
        wrappedStdout.manualClearNextBytes(numberOfBytes)
    }

    override fun close() {
        try {
            interpreter.inputStream.close()
        } catch (ignored: Exception) {
        }

        try {
            interpreter.errorStream.close()
        } catch (ignored: Exception) {
        }

        try {
            interpreter.outputStream.close()
        } catch (ignored: Exception) {
        }

        try {
            interpreter.destroy()
        } catch (ignored: Exception) {
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CephFSCommandRunner::class.java)
    }
}

fun InputStream.readLineUnbuffered(charset: Charset = Charsets.UTF_8): String {
    val lineBuilder = ArrayList<Byte>()
    var next = read()
    while (next != -1) {
        if (next == '\n'.toInt()) {
            break
        } else {
            lineBuilder.add(next.toByte())
        }

        next = read()
    }
    return String(lineBuilder.toByteArray(), charset)
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

enum class InterpreterCommand(val command: String) {
    LIST_DIRECTORY("list-directory"),
    READ("read"),
    READ_OPEN("read-open"),
    STAT("stat"),
    MKDIR("make-dir"),
    DELETE("delete"),
    MOVE("move"),
    TREE("tree"),
    SYMLINK("symlink"),
    SETFACL("setfacl"),
    COPY("copy"),
    WRITE("write"),
    WRITE_OPEN("write-open"),
    GET_XATTR("get-xattr"),
    SET_XATTR("set-xattr"),
    LIST_XATTR("list-xattr"),
    DELETE_XATTR("delete-xattr"),
    CHMOD("chmod"),
}

