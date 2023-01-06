package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.IngoingCallResponse
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

val outputFolder by lazy {
    if (File("../../backend").exists() && File("../../frontend-web").exists()) {
        File("../../docs").apply {
            deleteRecursively()
            mkdirs()
        }

    } else {
        throw IllegalStateException("Unable to find UCloud code at ${File(".").absolutePath}. " +
                "Make sure that this script is run with the backend/launcher as current working directory.")
    }
}

fun badge(
    label: String,
    message: String,
    color: String,
    altText: String = "$label: $message",
): String {
    return "![$altText](https://img.shields.io/static/v1?label=$label&message=${message.replace(" ", "+")}&color=$color&style=flat-square)"
}

fun apiMaturityBadge(level: UCloudApiMaturity): String {
    val label = "API"
    fun normalizeEnum(enum: Enum<*>): String {
        return enum.name.lowercase().capitalize()
    }
    val badge = when (level) {
        is UCloudApiMaturity.Internal -> badge(label, "Internal/${normalizeEnum(level.level)}", "red")
        is UCloudApiMaturity.Experimental -> badge(label, "Experimental/${normalizeEnum(level.level)}", "orange")
        UCloudApiMaturity.Stable -> badge(label, "Stable", "green")
        else -> error("unknown level")
    }
    return "[$badge](/docs/developer-guide/core/api-conventions.md)"
}

fun rolesBadge(roles: Set<Role>): String {
    val message = when (roles) {
        Roles.AUTHENTICATED -> "Authenticated"
        Roles.PRIVILEGED, Roles.SERVICE -> "Services"
        Roles.END_USER -> "Users"
        Roles.ADMIN -> "Admin"
        Roles.PUBLIC -> "Public"
        Roles.PROVIDER -> "Provider"
        else -> roles.joinToString(", ")
    }

    val badge = badge("Auth", message, "informational")
    return "[$badge](/docs/developer-guide/core/types.md#role)"
}

fun deprecatedBadge(deprecated: Boolean): String {
    if (deprecated) {
        val badge = badge("Deprecated", "Yes", "red")
        return "[$badge](/docs/developer-guide/core/api-conventions.md)"
    }
    return ""
}

fun summary(summary: String, body: String, open: Boolean = false): String {
    return buildString {
        if (open) {
            appendLine("<details open>")
        } else {
            appendLine("<details>")
        }
        appendLine("<summary>")

        appendLine(summary)

        appendLine("</summary>")

        // NOTE(Dan): Force markdown rendering by adding empty lines
        appendLine()
        appendLine(body)
        appendLine()

        appendLine("</details>")
    }
}

fun generateMarkdownChapterTableOfContents(
    previousSection: Chapter?,
    nextSection: Chapter?,
    path: List<Chapter.Node>,
    chapter: Chapter.Node
) {
    val outputFile = File(
        outputFolder,
        path.joinToString("/") { it.id.replace("/", "_") } + "/" + chapter.id + "/" + "README.md"
    )
    outputFile.parentFile.mkdirs()

    outputFile.printWriter().use { outs ->
        /*
        val urlBuilder = StringBuilder("/docs/")
        for ((index, node) in (path + chapter).withIndex()) {
            if (index != 0) outs.print(" / ")
            urlBuilder.append(node.id)
            urlBuilder.append('/')

            if (node == chapter) {
                outs.print("${node.title}")
            } else {
                outs.print("[${node.title}](${urlBuilder}README.md)")
            }
        }
        outs.println()
         */

        outs.println(generateSectionNavigation(previousSection, nextSection))
        outs.println(chapter.breadcrumbs())

        outs.println("# ${chapter.title}")
        outs.println()

        chapter.children.forEach { chapter ->
            outs.println(" - [${chapter.title}](${chapter.linkToDocs()})")
        }
    }
}

fun Chapter.linkToDocs(): String {
    val urlBuilder = StringBuilder("/docs/")
    for (node in path) {
        urlBuilder.append(node.id)
        urlBuilder.append('/')
    }

    val suffix = when (this) {
        is Chapter.Node -> "/README.md"
        is Chapter.Feature -> ".md"
        is Chapter.ExternalMarkdown -> ".md"
    }

    return urlBuilder.toString() + id + suffix
}

fun Chapter.breadcrumbs(includeLeaf: Boolean = false): String {
    return buildString {
        for ((index, node) in (path + this@breadcrumbs).withIndex()) {
            if (index != 0) append(" / ")

            if (node == this@breadcrumbs && !includeLeaf) {
                append("${node.title}")
            } else {
                append("[${node.title}](${node.linkToDocs()})")
            }
        }
    }
}

fun generateSectionNavigation(
    previousSection: Chapter?,
    nextSection: Chapter?
): String {
    return buildString {
        appendLine("<p align='center'>")
        if (previousSection != null) {
            val linkedSection = if (previousSection is Chapter.Node) {
                previousSection.children.lastOrNull() ?: previousSection
            } else {
                previousSection
            }

            appendLine("<a href='${linkedSection.linkToDocs()}'>« Previous section</a>")
            repeat(153) { append("&nbsp;") }
        }
        if (nextSection != null) {
            val linkedSection = if (nextSection is Chapter.Node) {
                nextSection.children.firstOrNull() ?: nextSection
            } else {
                nextSection
            }
            appendLine("<a href='${linkedSection.linkToDocs()}'>Next section »</a>")
        }
        appendLine("</p>")
        appendLine()
    }
}

fun generateExternalMarkdown(
    previousSection: Chapter?,
    nextSection: Chapter?,
    path: List<Chapter.Node>,
    chapter: Chapter.ExternalMarkdown
) {
    val title = chapter.title
    val id = chapter.id

    val outputFile = File(
        outputFolder,
        path.joinToString("/") { it.id.replace("/", "_") } + "/" + id + ".md"
    )

    outputFile.parentFile.mkdirs()
    outputFile.printWriter().use { outs ->
        outs.println(generateSectionNavigation(previousSection, nextSection))

        outs.println(chapter.breadcrumbs())

        outs.println("# $title")
        outs.println()
        outs.println(File(chapter.externalFile).readText())
    }
}

fun generateMarkdown(
    previousSection: Chapter?,
    nextSection: Chapter?,
    path: List<Chapter.Node>,
    types: LinkedHashMap<String, GeneratedType>,
    calls: List<GeneratedRemoteProcedureCall>,
    chapter: Chapter.Feature
) {
    val title = chapter.title
    val id = chapter.id
    val container = chapter.container

    val outputFile = File(
        outputFolder,
        path.joinToString("/") { it.id.replace("/", "_") } + "/" + id + ".md"
    )
    val referenceFolder = File(outputFolder, "reference").also { it.mkdirs() }

    outputFile.parentFile.mkdirs()
    val sortedCalls = calls.sortedWith(
        Comparator
            .comparingInt<GeneratedRemoteProcedureCall> {
                when (it.realCall.authDescription.access) {
                    AccessRight.READ -> 1
                    AccessRight.READ_WRITE -> 2
                }
            }
            .thenComparingInt { -1 * it.doc.importance }
            .thenComparing<String> { it.name }
    ).filter { it.doc.importance >= 0 }

    val sortedTypes = ArrayList(types.values).filter { it.owner == container::class }.sortedWith(
        Comparator
            .comparingInt<GeneratedType> {
                if (it.name.contains("Request")) {
                    1
                } else if (it.name.contains("Response")) {
                    2
                } else {
                    0
                }
            }
            .thenComparing<Int> { -1 * it.doc.importance }
            .thenComparing<String> { it.name }
    ).filter { it.doc.importance >= 0 }

    outputFile.printWriter().use { outs ->
        val documentation = container::class.java.documentation()
        val synopsis = container.description?.substringBefore('\n', "")?.takeIf { it.isNotEmpty() }
            ?.let { processDocumentation(container::class.java.packageName, it) }
            ?: documentation.synopsis
        val description = (container.description?.substringAfter('\n', "")?.takeIf { it.isNotEmpty() }
            ?.let { processDocumentation(container::class.java.packageName, it) }
            ?: documentation.description)?.trim()

        outs.println(generateSectionNavigation(previousSection, nextSection))

        outs.println(chapter.breadcrumbs())

        outs.println("# $title")
        outs.println()
        outs.println(apiMaturityBadge(documentation.maturity))
        outs.println()
        if (synopsis != null) outs.println("_${synopsis}_")
        outs.println()
        if (description != null) {
            if (!description.startsWith("#")) {
                outs.println("## Rationale")
                outs.println()
            }
            outs.println(description)
            outs.println()
        }

        if (container.useCases.isNotEmpty() || sortedCalls.isNotEmpty() || sortedTypes.isNotEmpty()) {
            outs.println("## Table of Contents")
        }
        var counter = 1
        if (container.useCases.isNotEmpty()) {
            outs.println(
                summary(
                    "<a href='#example-${container.useCases.first().title.replace(" ", "-").lowercase()}'>" +
                        "${counter++}. Examples" +
                        "</a>",
                    buildString {
                        appendLine("<table><thead><tr>")
                        appendLine("<th>Description</th>")
                        appendLine("</tr></thread>")
                        appendLine("<tbody>")
                        for (useCase in container.useCases) {
                            appendLine("<tr><td><a href='#example-${useCase.title.replace(" ", "-").lowercase()}'>${useCase.title}</a></td></tr>")
                        }
                        appendLine("</tbody></table>")
                    }
                )
            )
        }
        if (sortedCalls.isNotEmpty()) {
            outs.println(
                summary(
                    "<a href='#remote-procedure-calls'>" +
                        "${counter++}. Remote Procedure Calls" +
                        "</a>",
                    buildString {
                        appendLine("<table><thead><tr>")
                        appendLine("<th>Name</th>")
                        appendLine("<th>Description</th>")
                        appendLine("</tr></thread>")
                        appendLine("<tbody>")
                        for (call in sortedCalls) {
                            appendLine("<tr>")
                            appendLine("<td><a href='#${call.name.lowercase()}'><code>${call.name}</code></a></td>")
                            appendLine("<td>${call.doc.synopsis ?: "<i>No description</i>"}</td>")
                            appendLine("</tr>")
                        }
                        appendLine("</tbody></table>")
                    }
                )
            )
        }
        if (sortedTypes.isNotEmpty()) {
            outs.println(summary(
                "<a href='#data-models'>" +
                    "${counter++}. Data Models" +
                    "</a>",
                buildString {
                    appendLine("<table><thead><tr>")
                    appendLine("<th>Name</th>")
                    appendLine("<th>Description</th>")
                    appendLine("</tr></thread>")
                    appendLine("<tbody>")
                    for (type in sortedTypes) {
                        appendLine("<tr>")
                        appendLine("<td><a href='#${simplifyName(type.name).lowercase()}'><code>${simplifyName(type.name)}</code></a></td>")
                        appendLine("<td>${type.doc.synopsis ?: "<i>No description</i>"}</td>")
                        appendLine("</tr>")
                    }
                    appendLine("</tbody></table>")
                }
            ))
        }

        for (useCase in container.useCases) {
            outs.println("## Example: ${useCase.title}")
            val content = generateMarkdownForExample(useCase)
            outs.println(content)

            File(referenceFolder, "${useCase.id}.md")
                .writeText(
                    buildString {
                        appendLine(chapter.breadcrumbs(includeLeaf = true))
                        appendLine()
                        appendLine("# Example: ${useCase.title}")
                        appendLine()
                        appendLine(content)
                    }
                )
        }

        if (sortedCalls.isNotEmpty()) {
            outs.println()
            outs.println("## Remote Procedure Calls")
            outs.println()
            for (call in sortedCalls) {
                outs.println("### `${call.name}`")
                outs.println()
                val content = generateMarkdownForRemoteProcedureCall(container, types, call)
                outs.println(content)

                File(referenceFolder, "${call.realCall.fullName}.md")
                    .writeText(
                        buildString {
                            appendLine(chapter.breadcrumbs(includeLeaf = true))
                            appendLine()
                            appendLine("# `${call.realCall.fullName}`")
                            appendLine()
                            appendLine(content)
                        }
                    )
            }
        }

        if (sortedTypes.isNotEmpty()) {
            outs.println()
            outs.println("## Data Models")
            outs.println()

            for (type in sortedTypes) {
                outs.println("### `${simplifyName(type.name)}`")
                val content = generateMarkdownForType(container, types, type)
                outs.println(content)
                outs.println()
                outs.println("---")
                outs.println()

                File(referenceFolder, type.name + ".md")
                    .writeText(
                        buildString {
                            appendLine(chapter.breadcrumbs(includeLeaf = true))
                            appendLine()
                            appendLine("# `${simplifyName(type.name)}`")
                            appendLine()
                            appendLine(content)
                        }
                    )
            }
        }
    }
}

fun generateMarkdownForRemoteProcedureCall(
    container: CallDescriptionContainer,
    types: LinkedHashMap<String, GeneratedType>,
    call: GeneratedRemoteProcedureCall
): String {
    val out = StringWriter()
    val outs = PrintWriter(out)

    outs.println(apiMaturityBadge(call.doc.maturity))
    outs.println(rolesBadge(call.roles))
    outs.println(deprecatedBadge(call.doc.deprecated))
    outs.println()
    if (call.doc.synopsis != null) outs.println("_${call.doc.synopsis}_")
    outs.println()

    outs.println("| Request | Response | Error |")
    outs.println("|---------|----------|-------|")
    outs.print("|")
    outs.print(call.requestType.kotlinWithLink(container, types).toMarkdown())
    outs.print("|")
    outs.print(call.responseType.kotlinWithLink(container, types).toMarkdown())
    outs.print("|")
    outs.print(call.errorType.kotlinWithLink(container, types).toMarkdown())
    outs.println("|")

    outs.println()
    if (call.doc.description != null) outs.println(call.doc.description)
    outs.println()

    val responseExamples = call.realCall.docOrNull?.responseExamples ?: emptyList()
    val successfulResponses = responseExamples.filter { it.statusCode.isSuccess() }
    val errorResponses = responseExamples.filter { !it.statusCode.isSuccess() }

    if (successfulResponses.isNotEmpty()) {
        outs.println("__Responses:__")
        outs.println()
        outs.println("| Status Code | Description |")
        outs.println("|-------------|-------------|")
        for (resp in successfulResponses) {
            outs.println("| `${resp.statusCode}` | ${resp.description} |")
        }
        outs.println()
    }

    if (errorResponses.isNotEmpty()) {
        outs.println("__Errors:__")
        outs.println()
        outs.println("| Status Code | Description |")
        outs.println("|-------------|-------------|")
        for (resp in errorResponses) {
            outs.println("| `${resp.statusCode}` | ${resp.description} |")
        }
        outs.println()
    }

    val useCaseExamples = call.realCall.docOrNull?.useCaseReferences ?: emptyList()
    if (useCaseExamples.isNotEmpty()) {
        outs.println("__Examples:__")
        outs.println()
        outs.println("| Example |")
        outs.println("|---------|")
        for (example in useCaseExamples) {
            outs.println("| [${example.description}](/docs/reference/${example.id}.md) |")
        }
        outs.println()
    }

    return out.toString()
}

fun generateMarkdownForExample(useCase: UseCase): String {
    val out = StringWriter()
    val outs = PrintWriter(out)

    outs.println("<table>")
    outs.println("<tr><th>Frequency of use</th><td>${useCase.frequencyOfUse.name.lowercase().capitalize()}</td></tr>")
    if (useCase.trigger != null) {
        outs.println("<tr><th>Trigger</th><td>${useCase.trigger}</td></tr>")
    }
    if (useCase.preConditions.isNotEmpty()) {
        outs.println("<tr><th>Pre-conditions</th><td><ul>")
        useCase.preConditions.forEach {
            outs.println("<li>${it}</li>")
        }
        outs.println("</ul></td></tr>")
    }
    if (useCase.postConditions.isNotEmpty()) {
        outs.println("<tr><th>Post-conditions</th><td><ul>")
        useCase.postConditions.forEach {
            outs.println("<li>${it}</li>")
        }
        outs.println("</ul></td></tr>")
    }
    val actors = useCase.nodes.filterIsInstance<UseCaseNode.Actor>()
    if (actors.isNotEmpty()) {
        outs.println("<tr>")
        outs.println("<th>Actors</th>")
        outs.println("<td><ul>")
        for (actor in actors) {
            outs.println("<li>${actor.description} (<code>${actor.name}</code>)</li>")
        }
        outs.println("</ul></td>")
        outs.println("</tr>")
    }
    outs.println("</table>")

    outs.appendLine(
        summary(
            "<b>Communication Flow:</b> Kotlin",
            buildString {
                appendLine("```kotlin")
                for (node in useCase.nodes) {
                    when (node) {
                        is UseCaseNode.Actor -> {
                            // Do nothing
                        }
                        is UseCaseNode.Call<*, *, *> -> {
                            if (node.name != null) {
                                append("val ")
                                append(node.name)
                                append(" = ")
                            }
                            append(node.call.containerRef::class.simpleName)
                            append(".")
                            append(node.call.field?.name ?: node.call.name)
                            appendLine(".call(")
                            append(generateKotlinFromValue(node.request).prependIndent("    "))
                            appendLine(",")
                            append("    ")
                            appendLine(node.actor.name)
                            appendLine(").orThrow()")
                            appendLine()
                            appendLine("/*")
                            if (node.name != null) {
                                append(node.name)
                                append(" = ")
                            }
                            when (val response = node.response) {
                                is IngoingCallResponse.Error -> {
                                    appendLine(response.statusCode.toString())
                                }
                                is IngoingCallResponse.Ok -> {
                                    appendLine(generateKotlinFromValue(response.result))
                                }
                            }
                            appendLine("*/")
                        }
                        is UseCaseNode.Comment -> {
                            appendLine()
                            appendLine("/* ${node.comment} */")
                            appendLine()
                        }
                        is UseCaseNode.SourceCode -> {
                            if (node.language == UseCaseNode.Language.KOTLIN) {
                                appendLine(node.code)
                            }
                        }

                        is UseCaseNode.Subscription<*, *, *> -> {
                            append(node.call.containerRef::class.simpleName)
                            append(".")
                            append(node.call.field?.name ?: node.call.name)
                            appendLine(".subscribe(")
                            append(generateKotlinFromValue(node.request).prependIndent("    "))
                            appendLine(",")
                            append("    ")
                            append(node.actor.name)
                            appendLine(",")
                            appendLine("    handler = { /* will receive messages listed below */ }")
                            appendLine(")")
                            appendLine()

                            for (message in node.messages) {
                                when (message) {
                                    is UseCaseNode.RequestOrResponse.Request -> {
                                        append(node.call.containerRef::class.simpleName)
                                        append(".")
                                        append(node.call.field?.name ?: node.call.name)
                                        appendLine(".call(")
                                        append(generateKotlinFromValue(message.request).prependIndent("    "))
                                        appendLine(",")
                                        append("    ")
                                        appendLine(node.actor.name)
                                        appendLine(").orThrow()")
                                        appendLine()
                                    }

                                    is UseCaseNode.RequestOrResponse.Response -> {
                                        appendLine("/*")
                                        appendLine(generateKotlinFromValue(message.response.result))
                                        appendLine("*/")
                                        appendLine()
                                    }

                                    is UseCaseNode.RequestOrResponse.Error -> {
                                        appendLine("/*")
                                        appendLine(message.error.statusCode)
                                        appendLine(generateKotlinFromValue(message.error))
                                        appendLine("*/")
                                        appendLine()
                                    }
                                }
                            }
                        }
                    }
                }
                appendLine("```")
            }
        )
    )

    outs.appendLine(
        summary(
            "<b>Communication Flow:</b> Curl",
            useCase.curl()
        )
    )

    outs.appendLine(
        summary(
            "<b>Communication Flow:</b> Visual",
            useCase.visual(),
            open = true
        )
    )
    return out.toString()
}

fun generateMarkdownForType(
    owner: CallDescriptionContainer,
    types: LinkedHashMap<String, GeneratedType>,
    type: GeneratedType
): String {
    val out = StringWriter()
    val outs = PrintWriter(out)
    outs.println()
    outs.println(apiMaturityBadge(type.doc.maturity))
    outs.println(deprecatedBadge(type.doc.deprecated))
    outs.println()

    if (type.doc.synopsis != null) outs.println("_${type.doc.synopsis}_")
    outs.println()
    outs.println("```kotlin")
    outs.print(type.kotlin())
    outs.println("```")
    if (type.doc.description != null) outs.println(type.doc.description)
    outs.println()

    fun generateProperties(properties: List<GeneratedType.Property>): String = buildString {
        for (property in properties) {
            appendLine(summary(
                buildString {
                    append("<code>")
                    append(property.name)
                    append("</code>: ")
                    append("<code>")
                    append(property.type.kotlinWithLink(owner, types).toMarkdown())
                    append("</code>")
                    if (property.doc.synopsis != null) {
                        append(" ")
                        append(property.doc.synopsis)
                    }
                },
                buildString {
                    if (property.doc.maturity != type.doc.maturity) {
                        appendLine(apiMaturityBadge(property.doc.maturity))
                    }
                    appendLine(deprecatedBadge(property.doc.deprecated))
                    appendLine()

                    if (property.doc.description != null) appendLine(property.doc.description)
                }
            ))
        }
    }

    val details = when (type) {
        is GeneratedType.Enum -> {
            buildString {
                for (option in type.options) {
                    appendLine(
                        summary(
                            buildString {
                                append("<code>")
                                append(option.name)
                                append("</code>")
                                if (option.doc.synopsis != null) {
                                    append(" ")
                                    append(option.doc.synopsis)
                                }
                            },
                            buildString {
                                if (option.doc.maturity != type.doc.maturity) {
                                    appendLine(apiMaturityBadge(option.doc.maturity))
                                }
                                appendLine(deprecatedBadge(option.doc.deprecated))
                                appendLine()

                                if (option.doc.description != null) appendLine(option.doc.description)
                            }
                        )
                    )
                }
            }
        }
        is GeneratedType.Struct -> {
            generateProperties(type.properties)
        }
        is GeneratedType.TaggedUnion -> {
            if (type.baseProperties.isNotEmpty()) {
                generateProperties(type.baseProperties)
            } else {
                null
            }
        }
    }

    if (details != null) outs.println(summary("<b>Properties</b>", details))

    return out.toString()
}

fun GeneratedType.kotlin(): String {
    return when (this) {
        is GeneratedType.Enum -> {
            buildString {
                appendLine("enum class ${name.substringAfterLast('.')} {")
                for (option in options) {
                    appendLine("    ${option.name},")
                }
                appendLine("}")
            }
        }
        is GeneratedType.Struct -> {
            buildString {
                append("data class ${name.substringAfterLast('.')}")
                if (generics.isNotEmpty()) {
                    append("<")
                    for ((index, generic) in generics.withIndex()) {
                        if (index != 0) append(", ")
                        append(generic)
                    }
                    append(">")
                }
                appendLine("(")
                for (property in properties) {
                    appendLine("    val ${property.name}: ${property.type.kotlin()},")
                }
                appendLine(")")
            }
        }
        is GeneratedType.TaggedUnion -> {
            buildString {
                append("sealed class ${name.substringAfterLast('.')}")
                if (generics.isNotEmpty()) {
                    append("<")
                    for ((index, generic) in generics.withIndex()) {
                        if (index != 0) append(", ")
                        append(generic)
                    }
                    append(">")
                }
                appendLine(" {")
                for (property in baseProperties) {
                    appendLine("    abstract val ${property.name}: ${property.type.kotlin()}")
                }
                if (baseProperties.isNotEmpty()) appendLine()
                for (option in options) {
                    appendLine("    class ${option.kotlin().substringAfterLast('.')} : ${name.substringAfterLast('.')}()")
                }
                appendLine("}")
            }
        }
    }
}

fun GeneratedTypeReference.kotlin(
    owner: CallDescriptionContainer? = null,
    visitedTypes: LinkedHashMap<String, GeneratedType>? = null,
): String {
    return kotlinWithLink(owner, visitedTypes).nodes.joinToString("") { it.text }
}

data class TypeReferenceWithDocLinks(val nodes: List<Node>) {
    data class Node(var text: String, val link: String? = null)

    fun toMarkdown(): String {
        return buildString {
            append("<code>")
            for (node in nodes) {
                if (node.link != null) append("<a href='${node.link}'>")
                append(node.text.replace("<", "&lt;").replace(">", "&gt;"))
                if (node.link != null) append("</a>")
            }
            append("</code>")
        }
    }
}

fun GeneratedTypeReference.kotlinWithLink(
    owner: CallDescriptionContainer?,
    visitedTypes: LinkedHashMap<String, GeneratedType>?,
): TypeReferenceWithDocLinks {
    val baseValue: List<Pair<String, String?>> = when (this) {
        is GeneratedTypeReference.Any -> listOf("Any" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/")
        is GeneratedTypeReference.Array -> buildList {
            add("List" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/")
            add("<" to null)
            valueType.kotlinWithLink(owner, visitedTypes).nodes.forEach { add(it.text to it.link) }
            add(">" to null)
        }
        is GeneratedTypeReference.Bool -> listOf(
            "Boolean" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/"
        )
        is GeneratedTypeReference.ConstantString -> listOf(
            "String /* \"${value}\" */" to null
        )
        is GeneratedTypeReference.Dictionary -> {
            listOf("JsonObject" to "https://kotlin.github.io/kotlinx.serialization/" +
                    "kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/" +
                    "-json-object/index.html")
        }
        is GeneratedTypeReference.Float32 -> listOf(
            "Float" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/"
        )
        is GeneratedTypeReference.Float64 -> listOf(
            "Double" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/"
        )
        is GeneratedTypeReference.Int16 -> listOf(
            "Short" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-short/"
        )
        is GeneratedTypeReference.Int32 -> listOf(
            "Int" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/"
        )
        is GeneratedTypeReference.Int64 -> listOf(
            "Long" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/"
        )
        is GeneratedTypeReference.Int8 -> listOf(
            "Byte" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte/"
        )
        is GeneratedTypeReference.Structure -> buildList {
            val link: String? = if (owner == null || visitedTypes == null) {
                null
            } else {
                val visitedType = visitedTypes[name]
                if (visitedType == null) {
                    null
                } else {
                    if (visitedType.owner != owner::class) {
                        "/docs/reference/${name}.md"
                    } else {
                        "#${simplifyName(name).lowercase()}"
                    }
                }
            }
            add(simplifyName(name) to link)
            if (generics.isNotEmpty()) {
                add("<" to null)
                for ((index, generic) in generics.withIndex()) {
                    if (index != 0) add(", " to null)
                    generic.kotlinWithLink(owner, visitedTypes).nodes.forEach { add(it.text to it.link) }
                }
                add(">" to null)
            }
        }
        is GeneratedTypeReference.Text -> listOf(
            "String" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/"
        )
        is GeneratedTypeReference.Void -> listOf(
            "Unit" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/"
        )
    }

    val combinedList = if (nullable) baseValue + ("?" to null) else baseValue
    return TypeReferenceWithDocLinks(combinedList.map { TypeReferenceWithDocLinks.Node(it.first, it.second) })
}

fun generateKotlinFromValue(
    value: Any?,
): String {
    if (value == null) return "null"

    return when (value) {
        is Array<*>, is List<*>, is Set<*> -> {
            buildString {
                append(
                    when (value) {
                        is Array<*> -> if (value.isEmpty()) "emptyArray" else "arrayOf"
                        is List<*> ->  if (value.isEmpty()) "emptyList" else "listOf"
                        is Set<*> -> if(value.isEmpty()) "emptySet" else "setOf"
                        else -> "listOf"
                    }
                )
                append("(")
                if (value is Iterable<*>) {
                    for ((index, item) in value.withIndex()) {
                        if (index != 0) append(", ")
                        append(generateKotlinFromValue(item))
                    }
                }
                append(")")
            }
        }
        is String -> value.split("\n").joinToString(""" + "\n" + """ + "\n    ") { "\"${it}\"" }
        is JsonObject -> {
            buildString {
                append("JsonObject(mapOf(")
                for ((k, v) in value) {
                    append("\"$k\"")
                    append(" to ")
                    append(generateKotlinFromValue(v))
                    append("),")
                }
                append("))")
            }
        }
        is BulkRequest<*> -> {
            buildString {
                append("bulkRequestOf(")
                value.items.forEachIndexed { index, any ->
                    if (index != 0) append(", ")
                    append(generateKotlinFromValue(any))
                }
                append(")")
            }
        }
        is Unit -> "Unit"
        is Boolean, is Number -> value.toString()
        is Enum<*> -> {
            buildString {
                append(value::class.simpleName)
                append('.')
                append(value.name)
            }
        }

        is Map<*, *> -> {
            buildString {
                append("mapOf(")
                var idx = 0
                for ((k, v) in value) {
                    if (idx != 0) append(", ")
                    append(generateKotlinFromValue(k))
                    append(" to ")
                    append(generateKotlinFromValue(v))
                    idx++
                }
                append(")")
            }
        }

        else -> {
            buildString {
                append(simplifyName(value::class.java.canonicalName))
                val isObject = value::class.objectInstance != null
                if (!isObject) {
                    val memberProperties = value::class.memberProperties
                    if (memberProperties.isNotEmpty()) {
                        appendLine("(")
                        for (member in memberProperties) {
                            append("    ")
                            append(member.name)
                            append(" = ")

                            @Suppress("UNCHECKED_CAST")
                            member as KProperty1<Any, Any>

                            append(generateKotlinFromValue(member.get(value)).prependIndent("    ").trim())
                            appendLine(", ")
                        }
                        append(")")
                    } else {
                        append("()")
                    }
                }
            }
        }
    }
}

fun simplifyName(qualifiedName: String): String {
    return qualifiedName.split(".").filter { it.firstOrNull()?.isUpperCase() == true }.joinToString(".")
}
