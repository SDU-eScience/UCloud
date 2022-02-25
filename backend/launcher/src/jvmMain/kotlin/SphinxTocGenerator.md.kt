package dk.sdu.cloud

import dk.sdu.cloud.calls.UseCase
import java.io.File

private var didInitSphinxState = false
private fun createSphinxOutputDir(): File {
    val outputDirectory = File("/tmp/sphinx-output")
    if (didInitSphinxState) return outputDirectory
    didInitSphinxState = true

    outputDirectory.deleteRecursively()
    outputDirectory.mkdir()
    return outputDirectory
}

fun generateSphinxTableOfContents(chapter: Chapter.Node) {
    val outputDirectory = createSphinxOutputDir()
    val title = (chapter.path + chapter).drop(1).joinToString(".") { it.id }.takeIf { it.isNotEmpty() }?.plus(".md")
    val outputFile = File(outputDirectory, title ?: "intro.md")
    outputFile.printWriter().use { w ->
        w.println("# ${chapter.title}")
        w.println()
        w.println("```{toctree}")
        w.println("---")
        w.println("maxdepth: 2")
        w.println("---")
        w.println()
        for (n in chapter.children) {
            val normalizedPath = (n.path + n).drop(1)
            if (n is Chapter.Node) {
                w.println(normalizedPath.joinToString(".") { it.id })
            } else {
                w.println("docs/developer-guide/${normalizedPath.joinToString("/") { it.id }}")
            }
        }
        w.println("```")
        w.println()
    }
}

fun generateSphinxCalls(calls: Collection<List<GeneratedRemoteProcedureCall>>) {
    val outputDirectory = createSphinxOutputDir()
    val outputFile = File(outputDirectory, "api.routines.md")
    outputFile.printWriter().use { w ->
        w.println("# Remote Procedure Calls")
        w.println()
        w.println("```{toctree}")
        w.println("---")
        w.println("maxdepth: 1")
        w.println("---")
        w.println()
        for (container in calls) {
            for (call in container) {
                w.println("docs/reference/${call.namespace}.${call.name}.md")
            }
        }
        w.println("```")
        w.println()
    }
}

fun generateSphinxTypes(types: Collection<GeneratedType>) {
    val outputDirectory = createSphinxOutputDir()
    val outputFile = File(outputDirectory, "api.types.md")
    outputFile.printWriter().use { w ->
        w.println("# Type Reference")
        w.println()
        w.println("```{toctree}")
        w.println("---")
        w.println("maxdepth: 1")
        w.println("---")
        w.println()
        for (type in types) {
            w.println("docs/reference/${type.name}.md")
        }
        w.println("```")
        w.println()
    }
}

fun generateSphinxExamples(examples: Collection<UseCase>) {
    val outputDirectory = createSphinxOutputDir()
    val outputFile = File(outputDirectory, "api.examples.md")
    outputFile.printWriter().use { w ->
        w.println("# Examples")
        w.println()
        w.println("```{toctree}")
        w.println("---")
        w.println("maxdepth: 1")
        w.println("---")
        w.println()
        for (example in examples) {
            w.println("docs/reference/${example.id}.md")
        }
        w.println("```")
        w.println()
    }
}