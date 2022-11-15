package dk.sdu.cloud

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

data class CommandBuilder(
    val executable: String,
    val args: MutableList<String> = mutableListOf(),
    val envs: MutableList<String> = mutableListOf()
) {
    init {
        args.add(executable)
    }

    fun addArg(arg: String, argValue: String? = null): CommandBuilder {
        args.add(arg)
        if (argValue != null) args.add(argValue)
        return this
    }

    fun addEnv(env: String, envValue: String): CommandBuilder {
        envs.add("${env}=${envValue}")
        return this
    }

    fun executeToText(): ProcessResultText {
        return startProcessAndCollectToString(args, envs)
    }

    fun executeToBinary(): ProcessResult {
        return startProcessAndCollectToMemory(args, envs)
    }
}

class ProcessResult(
    val statusCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

data class ProcessResultText(
    val statusCode: Int,
    val stdout: String,
    val stderr: String,
)

fun startProcessAndCollectToString(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: InputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
    workingDir: File? = null,
    deadlineInMillis: Long = 1000 * 60 * 5,
): ProcessResultText {
    val res = startProcessAndCollectToMemory(args, envs, stdin, stdoutMaxSizeInBytes, stderrMaxSizeIntBytes, workingDir, deadlineInMillis)
    return ProcessResultText(res.statusCode, res.stdout.decodeToString().trim(), res.stderr.decodeToString().trim())
}

fun startProcessAndCollectToMemory(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: InputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
    workingDir: File? = null,
    deadlineInMillis: Long = 1000 * 60 * 5,
): ProcessResult {
    val deadline = System.currentTimeMillis() + deadlineInMillis
    val process = startProcess(
        args,
        envs = envs,
        attachStdin = stdin != null,
        attachStdout = stdoutMaxSizeInBytes > 0,
        attachStderr = stdoutMaxSizeInBytes > 0,
        nonBlockingStdout = true,
        nonBlockingStderr = true,
        workingDir = workingDir
    )

    if (stdin != null && process.stdin != null) {
        stdin.copyTo(process.stdin)
    }

    var stdoutPtr = 0
    var isStdoutOpen = true
    val stdoutBuffer = ByteArray(stdoutMaxSizeInBytes)

    var stderrPtr = 0
    var isStderrOpen = true
    val stderrBuffer = ByteArray(stderrMaxSizeIntBytes)

    var status = ProcessStatus(-1)
    while ((isStdoutOpen || isStderrOpen)) {
        if (process.stdout != null) {
            val bytesToRead = stdoutMaxSizeInBytes - stdoutPtr
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                if (process.stdout.available() > 0) {
                    val read = process.stdout.read(stdoutBuffer, stdoutPtr, bytesToRead)
                    if (read == -1) {
                        isStdoutOpen = false
                    } else {
                        stdoutPtr += read
                    }
                }
            } catch (ex: IOException) {
                isStdoutOpen = false
            }
        }

        if (process.stderr != null) {
            val bytesToRead = stderrMaxSizeIntBytes - stderrPtr
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                if (process.stderr.available() > 0) {
                    val read = process.stderr.read(stderrBuffer, stderrPtr, bytesToRead)
                    if (read == -1) {
                        isStderrOpen = false
                    } else {
                        stderrPtr += read
                    }
                }
            } catch (ex: IOException) {
                isStderrOpen = false
            }
        }

        status = process.retrieveStatus(waitForExit = false)
        if (System.currentTimeMillis() >= deadline) {
            runCatching {
                process.stderr?.close()
                process.stdout?.close()
                process.stdin?.close()
                process.jvm.destroyForcibly()
            }
            return ProcessResult(255, stdoutBuffer.copyOf(stdoutPtr), stderrBuffer.copyOf(stderrPtr))
        }

        if (status.isRunning) {
            Thread.sleep(15)
        } else {
            if ((process.stdout?.available() ?: 0) == 0 && (process.stderr?.available() ?: 0) == 0) {
                break
            }
            Thread.sleep(15)
        }
    }

    if (status.isRunning) {
        status = process.retrieveStatus(waitForExit = true)
    }

    process.stderr?.close()
    process.stdout?.close()
    process.stdin?.close()

    return ProcessResult(status.getExitCode(), stdoutBuffer.copyOf(stdoutPtr), stderrBuffer.copyOf(stderrPtr))
}

fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    attachStdin: Boolean = false,
    attachStdout: Boolean = true,
    attachStderr: Boolean = true,
    nonBlockingStdout: Boolean = false,
    nonBlockingStderr: Boolean = false,
    workingDir: File? = null,
): Process {
    val jvmProcess = ProcessBuilder().apply {
        command(args)
        environment().putAll(
            envs.associate {
                val key = it.substringBefore('=')
                val value = it.substringAfter('=')
                key to value
            }
        )

        if (workingDir != null) directory(workingDir)

        if (!attachStdout) redirectOutput(ProcessBuilder.Redirect.DISCARD)
        if (!attachStderr) redirectError(ProcessBuilder.Redirect.DISCARD)
    }.start()

    if (!attachStdin) jvmProcess.outputStream.close()

    return Process(jvmProcess)
}

class Process(
    val jvm: java.lang.Process
) {
    val stdin: OutputStream? = jvm.outputStream
    val stdout: InputStream? = jvm.inputStream
    val stderr: InputStream? = jvm.errorStream
    val pid: Long
        get() = jvm.pid()

    fun retrieveStatus(waitForExit: Boolean): ProcessStatus {
        if (waitForExit) {
            return ProcessStatus(jvm.waitFor())
        }
        val exitCode = try {
            jvm.exitValue()
        } catch (ex: IllegalThreadStateException) {
            -1
        }

        return ProcessStatus(exitCode)
    }
}

@JvmInline
value class ProcessStatus(private val status: Int) {
    val isRunning: Boolean get() = status < 0
    fun getExitCode(): Int {
        if (isRunning) throw IllegalStateException("Process is still running")
        return status
    }
}
