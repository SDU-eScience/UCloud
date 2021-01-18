package dk.sdu.cloud

import io.swagger.v3.oas.models.OpenAPI
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private sealed class MarkdownReference {
    abstract val lineStart: Int
    abstract val lineEnd: Int
    abstract fun generate(
        api: OpenAPI,
        typeRegistry: Map<String, ComputedType>,
    ): String

    data class TypeDocRef(
        val typeName: String,
        override val lineStart: Int,
        override val lineEnd: Int,
    ) : MarkdownReference() {
        override fun generate(api: OpenAPI, typeRegistry: Map<String, ComputedType>): String {
            return typeRegistry[typeName]?.documentation ?: ""
        }

        companion object {
            const val command = "typeref"
        }
    }
}

private data class MarkdownDocument(
    val file: File,
    val readData: String,
    val commands: List<MarkdownReference>,
) {
    companion object {
        fun fromFile(file: File): MarkdownDocument? {
            if (!file.exists() || !file.isFile || file.extension != "md") return null

            var refInProgress: MarkdownReference? = null
            val text = file.readText()
            val commands = ArrayList<MarkdownReference>()

            for ((index, line) in text.lines().withIndex()) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("<!--") && trimmedLine.endsWith("-->")) {
                    val command = trimmedLine.removePrefix("<!--").removeSuffix("-->").trim().split(":")
                    val isEnd = command.firstOrNull()?.startsWith("/") == true
                    if (!isEnd) {
                        when (command.firstOrNull()) {
                            MarkdownReference.TypeDocRef.command -> {
                                val typeName = command.getOrNull(1)
                                if (typeName != null) {
                                    refInProgress = MarkdownReference.TypeDocRef(typeName, index, -1)
                                }
                            }
                        }
                    } else {
                        if (refInProgress == null) continue
                        when (command.firstOrNull()?.removePrefix("/")) {
                            MarkdownReference.TypeDocRef.command -> {
                                if (refInProgress is MarkdownReference.TypeDocRef) {
                                    commands.add(refInProgress.copy(lineEnd = index))
                                    refInProgress = null
                                }
                            }
                        }
                    }
                }
            }

            if (commands.isEmpty()) return null
            return MarkdownDocument(file, file.readText(), commands)
        }
    }
}

fun injectMarkdownDocs(
    api: OpenAPI,
    typeRegistry: Map<String, ComputedType>,
) {
    val hasServices = File(".").listFiles()?.any { it.isDirectory && it.name.endsWith("-service") } == true
    if (!hasServices) {
        println("WARN: Could not find UCloud services at current working directory (${File(".").absolutePath}")
        println("WARN: No markdown documentation will be injected!")
        return
    }

    val documents = ArrayList<MarkdownDocument?>()
    documents.add(MarkdownDocument.fromFile(File("./README.md")))

    (File(".").listFiles() ?: emptyArray())
        .filter { it.isDirectory && it.name.endsWith("-service") }
        .forEach { serviceDir ->
            documents.add(MarkdownDocument.fromFile(File(serviceDir, "README.md")))
            val wiki = File(serviceDir, "wiki").takeIf { it.exists() } ?: return@forEach
            wiki
                .walkTopDown()
                .onEnter { (it.isFile && it.extension == "md") || it.isDirectory }
                .forEach { if (it.isFile) documents.add(MarkdownDocument.fromFile(it)) }
        }

    for (doc in documents) {
        if (doc == null) continue

        val tmpFile = Files.createTempFile("inprogress", ".md").toFile()
        val writer = tmpFile.bufferedWriter()
        writer.use { _ ->
            val commandIterator = doc.commands.iterator()
            var command: MarkdownReference? = commandIterator.next()
            var commandText: String? = command?.generate(api, typeRegistry)

            val lines = doc.readData.lines()
            for ((index, line) in lines.withIndex()) {
                if (commandText != null && command != null && index > command.lineStart && index < command.lineEnd) {
                    // Do nothing
                } else if (command != null && index == command.lineEnd) {
                    command = if (commandIterator.hasNext()) commandIterator.next() else null
                    if (commandText != null) writer.appendLine(commandText)
                    commandText = command?.generate(api, typeRegistry)
                    writer.appendLine(line)
                } else {
                    if (index == lines.lastIndex) {
                        writer.append(line)
                    } else {
                        writer.appendLine(line)
                    }
                }
            }
        }

        Files.move(
            tmpFile.toPath(),
            doc.file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}
