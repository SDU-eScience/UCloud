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

    fun typename(type: ComputedType): String {
        val baseName = when (type) {
            is ComputedType.Integer -> {
                when (type.size) {
                    8 -> "Byte"
                    16 -> "Short"
                    32 -> "Int"
                    64 -> "Double"
                    else -> "BigInteger"
                }
            }
            is ComputedType.FloatingPoint -> {
                when(type.size) {
                    32 -> "Float"
                    64 -> "Double"
                    else -> "BigDecimal"
                }
            }
            is ComputedType.Text -> "String"
            is ComputedType.Bool -> "Boolean"
            is ComputedType.Array -> "Array<${typename(type.itemType)}>"
            is ComputedType.Dictionary -> "Map<String, ${typename(type.itemType)}>"
            is ComputedType.Unknown -> "Any"
            is ComputedType.Generic -> "Any"
            is ComputedType.Struct -> typename(type.asRef())
            is ComputedType.StructRef -> type.qualifiedName.substringAfterLast('.')
            is ComputedType.Enum -> type.options.joinToString(" | ") { "\"$it\""}
            is ComputedType.CustomSchema -> "Any"
        }

        return if (type.optional) "$baseName?"
        else baseName
    }

    data class TypeDocRef(
        val typeName: String,
        val includeOwnDoc: Boolean,
        val includeProps: Boolean,
        val includePropDoc: Boolean,
        override val lineStart: Int,
        override val lineEnd: Int,
    ) : MarkdownReference() {
        override fun generate(api: OpenAPI, typeRegistry: Map<String, ComputedType>): String {
            return buildString {
                val type = typeRegistry[typeName]
                if (type != null) {
                    if (includeOwnDoc && type.documentation != null) {
                        appendLine(type.documentation!!.lines()[0])
                        appendLine()
                    }

                    if (includeProps && type is ComputedType.Struct) {
                        appendLine("| Property | Type | Description |")
                        appendLine("|----------|------|-------------|")
                        type.properties.forEach { (prop, propType) ->
                            appendLine("| `$prop` | `${typename(propType)}` | ${propType.documentation?.lines()?.firstOrNull() ?: "No documentation"} |")
                        }

                        appendLine()
                    }

                    if (includeProps && type is ComputedType.Enum) {
                        appendLine("| Enum | Description |")
                        appendLine("|------|-------------|")
                        type.options.forEach { enum ->
                            // TODO Documentation missing for enums
                            appendLine("| `\"$enum\"` | No documentation |")
                        }
                        appendLine()
                    }

                    if (includeOwnDoc && type.documentation != null) {
                        append(type.documentation!!.lines().drop(1).joinToString("\n"))
                        appendLine()
                        appendLine()
                    }

                    if (includePropDoc && type is ComputedType.Struct) {
                        type.properties.forEach { (prop, propType) ->
                            appendLine("---")
                            appendLine("`${prop}`: ${propType.documentation ?: "No documentation"}")
                            appendLine()
                        }
                        appendLine()
                    }
                } else {
                    append("UNKNOWN TYPE: $typeName")
                }
            }
        }

        companion object {
            const val command = "typedoc"
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
                    val args = command.drop(1).map { it.split("=") }.filter { it.size == 2 }
                        .associateBy { it[0] }.mapValues { it.value[1] }

                    val isEnd = command.firstOrNull()?.startsWith("/") == true
                    if (!isEnd) {
                        when (command.firstOrNull()) {
                            MarkdownReference.TypeDocRef.command -> {
                                val typeName = command.getOrNull(1)
                                if (typeName != null) {
                                    refInProgress = MarkdownReference.TypeDocRef(
                                        typeName,
                                        includeOwnDoc = (args["includeOwnDoc"] ?: "true") == "true",
                                        includeProps = (args["includeProps"] ?: "false") == "true",
                                        includePropDoc = (args["includePropDoc"] ?: "false") == "true",
                                        lineStart = index,
                                        lineEnd = -1
                                    )
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
                    if (commandText != null) {
                        writer.appendLine("<!--<editor-fold desc=\"Generated documentation\">-->")
                        writer.appendLine(commandText)
                        writer.appendLine("<!--</editor-fold>-->")
                    }
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
