package dk.sdu.cloud.integration

import java.io.File
import kotlin.system.exitProcess

@Suppress("NOTHING_TO_INLINE")
inline fun printStatus(line: String) {}

data class ExeCommand(
    val args: List<String>,
    val workingDir: File? = null,
    var allowFailure: Boolean = false,
    var deadlineInMillis: Long = 1000 * 60 * 5,
    var streamOutput: Boolean = false,
) {
    fun executeToText(): Pair<String?, String> {
        val deadline = System.currentTimeMillis() + deadlineInMillis

        val process = ProcessBuilder(args).apply {
            if (workingDir != null) {
                directory(workingDir)
            }
        }.start()

        process.outputStream.close()
        val output = process.inputStream.bufferedReader()
        val error = process.errorStream.bufferedReader()

        val outputBuilder = StringBuilder()
        val errBuilder = StringBuilder()
        val stdoutThread = Thread {
            while (System.currentTimeMillis() < deadline) {
                val line = output.readLine() ?: break
                if (streamOutput) printStatus(line)
                outputBuilder.appendLine(line)
            }
        }.also { it.start() }

        val stderrThread = Thread {
            while (System.currentTimeMillis() < deadline) {
                val line = error.readLine() ?: break
                if (streamOutput) printStatus(line)
                errBuilder.appendLine(line)
            }
        }.also { it.start() }

        stdoutThread.join()
        stderrThread.join()
        runCatching {
            output.close()
            error.close()

            if (process.isAlive) process.destroy()
        }

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            if (allowFailure) return Pair(null, outputBuilder.toString() + errBuilder.toString())
            println("Command failed!")
            println("Command: " + args.joinToString(" ") { "'$it'" })
            println("Directory: $workingDir")
            println("Exit code: ${exitCode}")
            println("Stdout: ${outputBuilder}")
            println("Stderr: ${errBuilder}")

            exitProcess(exitCode)
        }
        return Pair(outputBuilder.toString(), errBuilder.toString())
    }
}
