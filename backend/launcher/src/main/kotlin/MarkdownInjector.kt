/*
package dk.sdu.cloud

import dk.sdu.cloud.service.SimpleCache
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
private val kotlinCompilerEnvironment by lazy {
    val configOptions = CompilerConfiguration().apply {
        put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    }

    KotlinCoreEnvironment.createForTests(
        Disposer.newDisposable(),
        configOptions,
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    )
}
*/

private sealed class MarkdownReference {
    abstract val lineStart: Int
    abstract var lineEnd: Int
    abstract fun generate(
        typeRegistry: Map<String, ComputedType>,
    ): String

    /*
    fun typename(type: ComputedType): String {
        val baseName = when (type) {
            is ComputedType.Integer -> {
                when (type.size) {
                    8 -> "Byte"
                    16 -> "Short"
                    32 -> "Int"
                    64 -> "Long"
                    else -> "BigInteger"
                }
            }
            is ComputedType.FloatingPoint -> {
                when (type.size) {
                    32 -> "Float"
                    64 -> "Double"
                    else -> "BigDecimal"
                }
            }
            is ComputedType.Text -> "String"
            is ComputedType.TextArea -> "String"
            is ComputedType.Bool -> "Boolean"
            is ComputedType.Array -> "Array<${typename(type.itemType)}>"
            is ComputedType.Dictionary -> "Map<String, ${typename(type.itemType)}>"
            is ComputedType.Unknown -> "Any"
            is ComputedType.Generic -> "Any"
            is ComputedType.Struct -> typename(type.asRef())
            is ComputedType.StructRef -> type.qualifiedName.substringAfterLast('.')
            is ComputedType.Enum -> "(" + type.options.joinToString(" or ") { "\"$it\"" } + ")"
        }

        return if (type.optional) "$baseName?"
        else baseName
    }
    */

    data class TypeDocRef(
        val typeName: String,
        val includeOwnDoc: Boolean,
        val includeProps: Boolean,
        val includePropDoc: Boolean,
        override val lineStart: Int,
        override var lineEnd: Int,
    ) : MarkdownReference() {
        override fun generate(typeRegistry: Map<String, ComputedType>): String {
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
                            appendLine("| `$prop` | `${typename(propType)}` | ${
                                propType.documentation?.lines()?.firstOrNull() ?: "No documentation"
                            } |")
                        }

                        if (type.properties.isEmpty()) {
                            appendLine("| - | - | This struct has no properties |")
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

    data class KotlinClassRef(
        val path: String,
        val classPath: String,
        val documentationOnly: Boolean,
        override val lineStart: Int,
        override var lineEnd: Int,
    ) : MarkdownReference() {
        override fun generate(typeRegistry: Map<String, ComputedType>): String {
            val ktFiles = KotlinFileCache.ktFileCache.getBlocking(path) ?: error("Error parsing file at '$path'")
            for (ktFile in ktFiles) {
                val components = classPath.split('.')
                val interfaceFunction = if (components.size >= 2) {
                    ktFile.children.find {
                        val interfaceName = components.lastOrNull() ?: return@find false
                        val expectedPackageName = components.dropLast(1).joinToString(".")
                        val packageName = ktFile.packageFqName.asString()
                        if (!(packageName == expectedPackageName || packageName.endsWith(expectedPackageName))) {
                            return@find false
                        }

                        it is KtClassOrObject && it.name == interfaceName
                    } as KtClassOrObject?
                } else {
                    null
                }

                if (interfaceFunction == null) continue

                if (documentationOnly) {
                    val docText = interfaceFunction.docComment?.text ?: "No documentation"
                    return unwrapDocComment(docText)
                }
                return "```kotlin\n${interfaceFunction?.text ?: continue}\n```"
            }

            error("Could not find class: '$path/$classPath'")
        }

        companion object {
            const val command = "ktclassref"
        }
    }

    data class KotlinFunRef(
        val path: String,
        val functionPath: String,
        val includeDocs: Boolean,
        val includeSignature: Boolean,
        val includeBody: Boolean,
        override val lineStart: Int,
        override var lineEnd: Int,
    ) : MarkdownReference() {
        override fun generate(typeRegistry: Map<String, ComputedType>): String {
            val ktFiles = KotlinFileCache.ktFileCache.getBlocking(path) ?: error("Error parsing file at '$path'")
            for (ktFile in ktFiles) {
                val topLevelFunction = ktFile.children.find {
                    val fnName = functionPath.substringAfterLast('.')
                    val expectedPackageName = functionPath.substringBeforeLast('.')
                    val packageName = ktFile.packageFqName.asString()
                    it is KtNamedFunction && it.name == fnName &&
                        (packageName == expectedPackageName || packageName.endsWith(expectedPackageName))
                } as KtNamedFunction?

                val components = functionPath.split('.')
                val interfaceFunction = if (components.size >= 3) {
                    ktFile.children.mapNotNull {
                        val fnName = components.lastOrNull() ?: return@mapNotNull null
                        val interfaceName = components.getOrNull(components.lastIndex - 1) ?: return@mapNotNull null
                        val expectedPackageName = components.dropLast(2).joinToString(".")
                        val packageName = ktFile.packageFqName.asString()
                        if (!(packageName == expectedPackageName || packageName.endsWith(expectedPackageName))) {
                            return@mapNotNull null
                        }

                        if (it is KtClass && it.name == interfaceName) {
                            it.declarations.find { decl ->
                                decl is KtNamedFunction && decl.name == fnName
                            } as KtNamedFunction?
                        } else {
                            null
                        }
                    }.singleOrNull()
                } else {
                    null
                }

                if (includeSignature) {
                    return topLevelFunction?.signatureToString(includeBody)
                        ?: interfaceFunction?.signatureToString(includeBody) ?: continue
                } else {
                    if (topLevelFunction == null && interfaceFunction == null) continue
                    val rawDocComment = topLevelFunction?.docComment?.text ?: interfaceFunction?.docComment?.text
                        ?: ""

                    return unwrapDocComment(rawDocComment)
                }
            }

            error("Could not find function: '$path/$functionPath'")
        }

        companion object {
            const val command = "ktfunref"
        }
    }

    object KotlinFileCache {
        val ktFileCache = SimpleCache<String, List<KtFile>>(SimpleCache.DONT_EXPIRE) { path ->
            File(path).walkTopDown().mapNotNull {
                if (it.isFile && it.extension == "kt") {
                    PsiManager
                        .getInstance(kotlinCompilerEnvironment.project)
                        .findFile(LightVirtualFile(it.name, KotlinFileType.INSTANCE, it.readText())) as KtFile
                } else {
                    null
                }
            }.toList()
        }
    }

    fun unwrapDocComment(docText: String): String {
        val docLines = docText.removePrefix("/**").removeSuffix("*/").lines()
        return docLines.filterIndexed { index, line ->
            when {
                index == 0 && line.isBlank() -> false
                index == docLines.lastIndex && line.isBlank() -> false
                line.trim().removePrefix("*").trim().startsWith("@") -> false
                else -> true
            }
        }.joinToString("\n") { it.trim().removePrefix("*").trim() }
    }

    fun KtNamedFunction.signatureToString(includeBody: Boolean): String {
        return buildString {
            appendLine("```kotlin")
            if (!includeBody) {
                appendLine(text.replace(children.find { it is KtExpression }?.text ?: "", ""))
            } else {
                appendLine(text)
            }
            appendLine("````")
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

                            MarkdownReference.KotlinFunRef.command -> {
                                val path = command.getOrNull(1)
                                val fnName = command.getOrNull(2)
                                if (path == null || fnName == null) {
                                    error("Incorrect usage: $line\n" +
                                        "Correct usage <!-- $command:<PATH>:<FN_NAME> -->")
                                }

                                refInProgress = MarkdownReference.KotlinFunRef(
                                    path,
                                    fnName,
                                    includeDocs = (args["includeDocs"] ?: "true") == "true",
                                    includeSignature = (args["includeSignature"] ?: "true") == "true",
                                    includeBody = (args["includeBody"] ?: "true") == "true",
                                    lineStart = index,
                                    lineEnd = -1,
                                )
                            }

                            MarkdownReference.KotlinClassRef.command -> {
                                val path = command.getOrNull(1)
                                val name = command.getOrNull(2)
                                if (path == null || name == null) {
                                    error("Incorrect usage: $line\n" +
                                        "Correct usage <!-- $command:<PATH>:<NAME> -->")
                                }

                                refInProgress = MarkdownReference.KotlinClassRef(
                                    path,
                                    name,
                                    documentationOnly = (args["documentationOnly"] ?: "false") == "true",
                                    lineStart = index,
                                    lineEnd = -1
                                )
                            }
                        }
                    } else {
                        if (refInProgress == null) continue
                        refInProgress.lineEnd = index
                        commands.add(refInProgress)
                        refInProgress = null
                    }
                }
            }

            if (commands.isEmpty()) return null
            return MarkdownDocument(file, file.readText(), commands)
        }
    }
}

fun injectMarkdownDocs(
    typeRegistry: Map<String, ComputedType>,
) {
    /*
    val hasServices = File(".").listFiles()?.any { it.isDirectory && it.name.endsWith("-service") } == true
    if (!hasServices) {
        println("WARN: Could not find UCloud services at current working directory (${File(".").absolutePath}")
        println("WARN: No markdown documentation will be injected!")
        return
    }

    val documents = ArrayList<MarkdownDocument?>()
    documents.add(MarkdownDocument.fromFile(File("./README.md")))

    (File(".").listFiles() ?: emptyArray())
        .filter { it.isDirectory && (it.name.endsWith("-service") || it.name == "service-lib") }
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
    */
}
*/
