package dk.sdu.cloud

sealed class FileFactory {
    abstract fun create(path: String): LFile
}

sealed class LFile(val path: String) {
    abstract val absolutePath: String
    val name: String = path.substringAfterLast('/')

    abstract fun exists(): Boolean
    abstract fun child(subpath: String): LFile
    abstract fun writeText(text: String)
    abstract fun writeBytes(bytes: ByteArray)
    abstract fun appendText(text: String)
    abstract fun delete()
    abstract fun mkdirs()

    override fun toString() = absolutePath
}

sealed class ExecutableCommandFactory {
    abstract fun create(
        args: List<String>,
        workingDir: LFile? = null,
        postProcessor: (result: ProcessResultText) -> String = { it.stdout },
        allowFailure: Boolean = false,
        deadlineInMillis: Long = 1000 * 60 * 5,
    ): ExecutableCommand
}

interface ExecutableCommand {
    val args: List<String>
    val workingDir: LFile?
    val postProcessor: (result: ProcessResultText) -> String
    var allowFailure: Boolean
    var deadlineInMillis: Long

    fun toBashScript(): String
    fun executeToText(): Pair<String?, String>
}