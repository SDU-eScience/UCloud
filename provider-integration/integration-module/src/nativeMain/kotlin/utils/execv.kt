package dk.sdu.cloud.utils

import dk.sdu.cloud.wexitstatus
import dk.sdu.cloud.wifexited
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.system.exitProcess
import kotlinx.cinterop.ByteVar as KotlinxCinteropByteVar

data class ProcessStreams(
    val stdin: Int? = null,
    val stdout: Int? = null,
    val stderr: Int? = null,
)

fun replaceThisProcess(args: List<String>, newStreams: ProcessStreams, envs: List<String> = listOf() ): Nothing {

    val nativeArgs = nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(args.size + 1)
    for (i in args.indices) {
        nativeArgs[i] = strdup(args[i])
    }
    nativeArgs[args.size] = null

    var nativeEnv =  nativeHeap.allocArray<CPointerVar<KotlinxCinteropByteVar>>(envs.size)
    for (i in envs.indices) {
        nativeEnv[i] = strdup(envs[i])
    }
    nativeEnv[envs.size] = null



    if (newStreams.stdin != null) {
        close(0)
        dup2(newStreams.stdin, 0)
    }

    if (newStreams.stdout != null) {
        close(1)
        dup2(newStreams.stdout, 1)
    }

    if (newStreams.stderr != null) {
        close(2)
        dup2(newStreams.stderr, 2)
    }




    execve(args[0], nativeArgs, nativeEnv)
    exitProcess(255)
}

fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    createStreams: () -> ProcessStreams
): Int {
    val forkResult = fork()


    if (forkResult == -1) {
        throw IllegalStateException("Could not start new process")
    } else if (forkResult == 0) {
        replaceThisProcess(args, createStreams(), envs = envs)
    }

    return forkResult
}

value class ProcessStatus(private val status: Int) {
    val isRunning: Boolean get() = status < 0
    fun getExitCode(): Int {
        if (isRunning) throw IllegalStateException("Process is still running")
        return status
    }
}

class Process(
    val stdin: NativeOutputStream?,
    val stdout: NativeInputStream?,
    val stderr: NativeInputStream?,
    val pid: Int,
) {
    fun retrieveStatus(waitForExit: Boolean): ProcessStatus {
        memScoped {
            val wstatus = alloc<IntVar>()
            val result = waitpid(pid, wstatus.ptr, if (waitForExit) 0 else WNOHANG)
            return when {
                result == 0 -> ProcessStatus(-1)
                result < 0 -> throw IllegalStateException(getNativeErrorMessage(errno))
                else -> ProcessStatus(
                    if (wifexited(wstatus.value)) wexitstatus(wstatus.value)
                    else 255
                )
            }
        }
    }
}

fun startProcess(
    args: List<String>,
    envs: List<String> = listOf(),
    attachStdin: Boolean = false,
    attachStdout: Boolean = true,
    attachStderr: Boolean = true,
    nonBlockingStdout: Boolean = false,
    nonBlockingStderr: Boolean = false,
): Process {
    memScoped {
        var stdinForChild: Int? = null
        var stdoutForChild: Int? = null
        var stderrForChild: Int? = null

        var stdinForParent: NativeOutputStream? = null
        var stdoutForParent: NativeInputStream? = null
        var stderrForParent: NativeInputStream? = null

        if (attachStdin) {
            val pipes = allocArray<IntVar>(2)
            if (pipe(pipes) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }

            stdinForChild = pipes[0]
            stdinForParent = NativeOutputStream(pipes[1])
        }

        if (attachStdout) {
            val pipes = allocArray<IntVar>(2)
            if (pipe(pipes) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }

            if (!nonBlockingStdout || fcntl(pipes[0], F_SETFL, fcntl(pipes[0], F_GETFL) or O_NONBLOCK) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }
            stdoutForParent = NativeInputStream(pipes[0])
            stdoutForChild = pipes[1]
        }

        if (attachStderr) {
            val pipes = allocArray<IntVar>(2)
            if (pipe(pipes) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }

            if (!nonBlockingStderr || fcntl(pipes[0], F_SETFL, fcntl(pipes[0], F_GETFL) or O_NONBLOCK) != 0) {
                throw IllegalStateException(getNativeErrorMessage(errno))
            }
            stderrForParent = NativeInputStream(pipes[0])
            stderrForChild = pipes[1]
        }

        

        val pid = startProcess(args, envs = envs) {
            ProcessStreams(stdinForChild, stdoutForChild, stderrForChild)
        }

        return Process(stdinForParent, stdoutForParent, stderrForParent, pid)
    }
}

class ProcessResult(
    val statusCode: Int,
    val stdout: ByteArray,
    val stderr: ByteArray,
)

fun startProcessAndCollectToMemory(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: NativeInputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
): ProcessResult {
    val process = startProcess(
        args,
        envs = envs,
        attachStdin = stdin != null,
        attachStdout = stdoutMaxSizeInBytes > 0,
        attachStderr = stdoutMaxSizeInBytes > 0,
        nonBlockingStdout = true,
        nonBlockingStderr = true,
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

    var status: ProcessStatus = ProcessStatus(-1)

    var breakOnNextIteration = false
    while (breakOnNextIteration || ((isStdoutOpen || isStderrOpen) && status.isRunning)) {
        if (process.stdout != null) {
            val bytesToRead = stdoutMaxSizeInBytes - stdoutPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                val read = process.stdout.read(stdoutBuffer, stdoutPtr, bytesToRead)

                val error = read.getErrorOrNull()
                if (error != EAGAIN && error != EWOULDBLOCK) {
                    stdoutPtr += read.getOrThrow()
                }
            } catch (ex: ReadException.EndOfFile) {
                isStdoutOpen = false
            }
        }

        if (process.stderr != null) {
            val bytesToRead = stderrMaxSizeIntBytes - stderrPtr
            // TODO Close the process
            if (bytesToRead <= 0) throw IllegalStateException("Max size has been exceeded")

            try {
                val read = process.stderr.read(stderrBuffer, stderrPtr, bytesToRead)
                val error = read.getErrorOrNull()
                if (error != EAGAIN && error != EWOULDBLOCK) {
                    stderrPtr += read.getOrThrow()
                }
            } catch (ex: ReadException.EndOfFile) {
                isStderrOpen = false
            }
        }

        if (breakOnNextIteration) break
        status = process.retrieveStatus(waitForExit = false)
        if (status.isRunning) {
            usleep(1000U)
        } else {
            breakOnNextIteration = true
        }
    }

    if (status.isRunning) {
        status = process.retrieveStatus(waitForExit = true)
    }
    return ProcessResult(status.getExitCode(), stdoutBuffer.copyOf(stdoutPtr), stderrBuffer.copyOf(stderrPtr))
}

data class ProcessResultText(
    val statusCode: Int,
    val stdout: String,
    val stderr: String,
)

fun startProcessAndCollectToString(
    args: List<String>,
    envs: List<String> = listOf(),
    stdin: NativeInputStream? = null,
    stdoutMaxSizeInBytes: Int = 1024 * 1024,
    stderrMaxSizeIntBytes: Int = 1024 * 1024,
): ProcessResultText {
    val res = startProcessAndCollectToMemory(args, envs, stdin, stdoutMaxSizeInBytes, stderrMaxSizeIntBytes)
    return ProcessResultText(res.statusCode, res.stdout.decodeToString().trim(), res.stderr.decodeToString().trim())
}

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

inline fun buildCommand(executable: String, block: CommandBuilder.() -> Unit): CommandBuilder {
    return CommandBuilder(executable).also(block)
}

inline fun executeCommandToText(executable: String, block: CommandBuilder.() -> Unit): ProcessResultText {
    return buildCommand(executable, block).executeToText()
}

inline fun executeCommandToBinary(executable: String, block: CommandBuilder.() -> Unit): ProcessResult {
    return buildCommand(executable, block).executeToBinary()
}
