package dk.sdu.cloud.utils

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.system.exitProcess

data class ProcessStreams(
    val stdin: Int?,
    val stdout: Int?,
    val stderr: Int?,
)

fun startProcess(
    args: List<String>,
    createStreams: () -> ProcessStreams,
) {
    val forkResult = fork()
    if (forkResult == -1) {
        throw IllegalStateException("Could not start new process")
    } else if (forkResult == 0) {
        val nativeArgs = nativeHeap.allocArray<CPointerVar<ByteVar>>(args.size + 1)
        for (i in args.indices) {
            nativeArgs[i] = strdup(args[i])
        }
        nativeArgs[args.size] = null

        val newStreams = createStreams()
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

        execv(args[0], nativeArgs)
        exitProcess(255)
    }
}
