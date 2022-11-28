package dk.sdu.cloud

import java.io.File
import kotlin.system.exitProcess

class LocalFileFactory : FileFactory() {
    override fun create(path: String) = LocalFile(path)
}

class LocalFile(path: String) : LFile(path) {
    val jvmFile = File(path)

    override val absolutePath: String
        get() = jvmFile.absolutePath

    override fun exists(): Boolean = jvmFile.exists()

    override fun child(subpath: String): LFile {
        return LocalFile(File(jvmFile, subpath).absolutePath)
    }

    override fun writeBytes(bytes: ByteArray) {
        jvmFile.writeBytes(bytes)
    }

    override fun writeText(text: String) {
        jvmFile.writeText(text)
    }

    override fun appendText(text: String) {
        jvmFile.appendText(text)
    }

    override fun delete() {
        jvmFile.deleteRecursively()
    }

    override fun mkdirs() {
        jvmFile.mkdirs()
    }
}

val debugCommands = System.getenv("DEBUG_COMMANDS") != null

class LocalExecutableCommandFactory() : ExecutableCommandFactory() {
    override fun create(
        args: List<String>,
        workingDir: LFile?,
        postProcessor: (result: ProcessResultText) -> String,
        allowFailure: Boolean,
        deadlineInMillis: Long,
        streamOutput: Boolean,
    ) = LocalExecutableCommand(args, workingDir as LocalFile?, postProcessor, allowFailure, deadlineInMillis,
        streamOutput)
}

data class LocalExecutableCommand(
    override val args: List<String>,
    override val workingDir: LocalFile? = null,
    override val postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    override var allowFailure: Boolean = false,
    override var deadlineInMillis: Long = 1000 * 60 * 5,
    override var streamOutput: Boolean = false,
) : ExecutableCommand {
    override fun toBashScript(): String {
        return buildString {
            if (workingDir != null) appendLine("cd '$workingDir'")
            appendLine(args.joinToString(" ") { "'${escapeBash(it)}'" })
        }
    }

    override fun executeToText(): Pair<String?, String> {
        if (debugCommands) println("Command: " + args.joinToString(" ") { "'$it'" })

        val deadline = System.currentTimeMillis() + deadlineInMillis

        val process = ProcessBuilder(args).apply {
            if (workingDir != null) {
                directory(workingDir.jvmFile)
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

        if (debugCommands) {
            println("  Exit code: ${exitCode}")
            println("  Stdout: ${outputBuilder}")
            println("  Stderr: ${errBuilder}")
        }

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
        return Pair(postProcessor(ProcessResultText(exitCode, outputBuilder.toString(), errBuilder.toString())), "")
    }
}
