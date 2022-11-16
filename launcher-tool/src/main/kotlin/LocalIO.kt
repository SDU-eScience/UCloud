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
        deadlineInMillis: Long
    ) = LocalExecutableCommand(args, workingDir as LocalFile?, postProcessor, allowFailure, deadlineInMillis)
}

data class LocalExecutableCommand(
    override val args: List<String>,
    override val workingDir: LocalFile? = null,
    override val postProcessor: (result: ProcessResultText) -> String = { it.stdout },
    override var allowFailure: Boolean = false,
    override var deadlineInMillis: Long = 1000 * 60 * 5,
) : ExecutableCommand {
    override fun toBashScript(): String {
        return buildString {
            if (workingDir != null) appendLine("cd '$workingDir'")
            appendLine(args.joinToString(" ") { "'$it'" })
        }
    }

    override fun executeToText(): Pair<String?, String> {
        if (debugCommands) println("Command: " + args.joinToString(" ") { "'$it'" })

        val result = startProcessAndCollectToString(
            args,
            workingDir = workingDir?.jvmFile,
            deadlineInMillis = deadlineInMillis
        )

        if (debugCommands) {
            println("  Exit code: ${result.statusCode}")
            println("  Stdout: ${result.stdout}")
            println("  Stderr: ${result.stderr}")
        }

        if (result.statusCode != 0) {
            if (allowFailure) return Pair(null, result.stdout + result.stderr)

            println("Command failed!")
            println("Command: " + args.joinToString(" ") { "'$it'" })
            println("Directory: $workingDir")
            println("Exit code: ${result.statusCode}")
            println("Stdout: ${result.stdout}")
            println("Stderr: ${result.stderr}")
            exitProcess(result.statusCode)
        }
        return Pair(postProcessor(result), "")
    }
}
