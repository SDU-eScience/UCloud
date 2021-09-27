package dk.sdu.cloud

import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.client.IngoingCallResponse
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.memberProperties

val outputFolder = File("/tmp/documentation").apply {
    deleteRecursively()
    mkdirs()
}

fun badge(
    label: String,
    message: String,
    color: String,
    altText: String = "$label: $message",
): String {
    return "![$altText](https://img.shields.io/static/v1?label=$label&message=$message&color=$color&style=flat-square)"
}

fun apiMaturityBadge(level: UCloudApiMaturity): String {
    val label = "API"
    fun normalizeEnum(enum: Enum<*>): String {
        return enum.name.lowercase().capitalize()
    }
    return when (level) {
        is UCloudApiMaturity.Internal -> badge(label, "Internal/${normalizeEnum(level.level)}", "red")
        is UCloudApiMaturity.Experimental -> badge(label, "Experimental/${normalizeEnum(level.level)}", "orange")
        UCloudApiMaturity.Stable -> badge(label, "Stable", "green")
        else -> error("unknown level")
    }
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

    return badge("Auth", message, "informational")
}

fun deprecatedBadge(deprecated: Boolean): String {
    if (deprecated) {
        return badge("Deprecated", "Yes", "red")
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

fun generateMarkdown(
    previousSection: Chapter?,
    nextSection: Chapter?,
    path: List<Chapter.Node>,
    types: LinkedHashMap<String, GeneratedType>,
    calls: List<GeneratedRemoteProcedureCall>,
    title: String,
    container: CallDescriptionContainer,
) {
    val outputFile = File(
        outputFolder,
        path.joinToString("/") { it.title.replace("/", "_") } + "/" + title + ".md"
    )

    outputFile.parentFile.mkdirs()

    outputFile.printWriter().use { outs ->
        val documentation = container::class.java.documentation()
        val synopsis = container.description?.substringBefore('\n', "")?.takeIf { it.isNotEmpty() }
            ?: documentation.synopsis
        val description = container.description?.substringAfter('\n', "")?.takeIf { it.isNotEmpty() }
            ?: documentation.description
        outs.println("# $title")
        outs.println()
        outs.println(apiMaturityBadge(documentation.maturity))
        outs.println()
        if (synopsis != null) outs.println("_${synopsis}_")
        outs.println()
        if (description != null) {
            outs.println("## Rationale")
            outs.println()
            outs.println(description)
            outs.println()
        }

        for (useCase in container.useCases) {
            outs.println("## Example: ${useCase.title}")
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
                                    appendLine("/* ${node.comment} */")
                                }
                                is UseCaseNode.SourceCode -> {
                                    if (node.language == UseCaseNode.Language.KOTLIN) {
                                        appendLine(node.code)
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
                    "<b>Communication Flow:</b> TypeScript",
                    useCase.typescript()
                )
            )

            outs.appendLine(
                summary(
                    "<b>Communication Flow:</b> Curl",
                    useCase.curl()
                )
            )
        }

        if (calls.isNotEmpty()) {
            outs.println()
            outs.println("## Remote Procedure Calls")
            outs.println()
            for (call in calls) {
                outs.println("### `${call.name}`")
                outs.println()
                outs.println(apiMaturityBadge(call.doc.maturity))
                outs.println(rolesBadge(call.roles))
                outs.println(deprecatedBadge(call.doc.deprecated))
                outs.println()
                if (call.doc.synopsis != null) outs.println("_${call.doc.synopsis}_")
                outs.println()

                outs.println("| Request | Response | Error |")
                outs.println("|---------|----------|-------|")
                outs.print("|")
                outs.print(call.requestType.kotlinWithLink().toMarkdown())
                outs.print("|")
                outs.print(call.responseType.kotlinWithLink().toMarkdown())
                outs.print("|")
                outs.print(call.errorType.kotlinWithLink().toMarkdown())
                outs.println("|")

                outs.println()
                if (call.doc.description != null) outs.println(call.doc.description)
                outs.println()
            }
        }

        if (types.values.isNotEmpty()) {
            outs.println()
            outs.println("## Data Models")
            outs.println()

            val sortedTypes = ArrayList(types.values).sortedWith(
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
            )
            for (type in sortedTypes) {
                if (type.owner != container::class) continue

                outs.println("### `${simplifyName(type.name)}`")

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
                                append(property.type.kotlin())
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

                outs.println()
                outs.println("---")
                outs.println()
            }
        }
    }
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

fun GeneratedTypeReference.kotlin(): String {
    return kotlinWithLink().nodes.joinToString("") { it.text }
}

data class TypeReferenceWithDocLinks(val nodes: List<Node>) {
    data class Node(var text: String, val link: String? = null)

    fun toMarkdown(): String {
        return buildString {
            append("<code>")
            for (node in nodes) {
                if (node.link != null) append("<a href='${node.link}'>")
                append(node.text)
                if (node.link != null) append("</a>")
            }
            append("</code>")
        }
    }
}

fun GeneratedTypeReference.kotlinWithLink(): TypeReferenceWithDocLinks {
    val baseValue: List<Pair<String, String?>> = when (this) {
        is GeneratedTypeReference.Any -> listOf("Any" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-any/")
        is GeneratedTypeReference.Array -> buildList {
            add("List" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/")
            add("<" to null)
            valueType.kotlinWithLink().nodes.forEach { add(it.text to it.link) }
            add(">" to null)
        }
        is GeneratedTypeReference.Bool -> listOf(
            "Boolean" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-boolean/"
        )
        is GeneratedTypeReference.ConstantString -> listOf(
            "String /* \"${value}\" */" to null
        )
        is GeneratedTypeReference.Dictionary -> listOf("JsonObject" to "https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html")
        is GeneratedTypeReference.Float32 -> listOf("Float" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-float/")
        is GeneratedTypeReference.Float64 -> listOf("Double" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-double/")
        is GeneratedTypeReference.Int16 -> listOf("Short" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-short/")
        is GeneratedTypeReference.Int32 -> listOf("Int" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-int/")
        is GeneratedTypeReference.Int64 -> listOf("Long" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-long/")
        is GeneratedTypeReference.Int8 -> listOf("Byte" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte/")
        is GeneratedTypeReference.Structure -> buildList {
            add(simplifyName(name) to null)
            if (generics.isNotEmpty()) {
                add("<" to null)
                for ((index, generic) in generics.withIndex()) {
                    if (index != 0) add(", " to null)
                    generic.kotlinWithLink().nodes.forEach { add(it.text to it.link) }
                }
                add(">" to null)
            }
        }
        is GeneratedTypeReference.Text -> listOf("String" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-byte/")
        is GeneratedTypeReference.Void -> listOf("Unit" to "https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/")
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
                        is Array<*> -> "arrayOf"
                        is List<*> -> "listOf"
                        is Set<*> -> "setOf"
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
        is String -> "\"${value}\""
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