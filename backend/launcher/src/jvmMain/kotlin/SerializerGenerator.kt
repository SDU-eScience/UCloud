package dk.sdu.cloud

import dk.sdu.cloud.calls.CallDescriptionContainer
import java.io.File


// TODO Issues:
// Default values are marked as nullable, which is problematic
// Some functions use Set and not List, they are all combined into one
// Some values are transient but they are added to the output regardless
// Some values are experimental, they should obviously be skipped
// Some values are not using the proper bounds for their generics
// parseJson() function does not handle generics at all (maybe just skip?)
// enums are not parsed correctly

val knownTypes = HashSet<String>()
val serializerBuilder = StringBuilder().apply {
    appendLine("@file:Suppress(\"RemoveRedundantBackticks\", \"FunctionName\", \"CascadeIf\")\n")
    appendLine("package dk.sdu.cloud")
}
fun generateSerializerCode(
    types: LinkedHashMap<String, GeneratedType>,
    calls: List<GeneratedRemoteProcedureCall>,
    container: CallDescriptionContainer
) {
    for ((qualifiedName, type) in types) {
        if (type is GeneratedType.Struct) {
            if (qualifiedName in knownTypes) continue
            knownTypes.add(qualifiedName)

            generateKotlinDeserializer(serializerBuilder, qualifiedName, type)
        }
    }
}

fun writeSerializer() {
    with(serializerBuilder) {
        appendLine("inline fun <reified T> parseJson(parser: JsonParser, hasStartedObject: Boolean = false): T {")
        appendLine("when (T::class.qualifiedName) {")
        for (type in knownTypes) {
            append('"')
            append(type)
            append('"')
            append(" -> return `")
            append(type.replace('.', '_'))
            appendLine("_deserialize`(parser, hasStartedObject) as T")
        }
        appendLine("else -> throw IllegalStateException(\"Unknown type: \${T::class.qualifiedName}\")")
        appendLine("}")
        appendLine("}")
    }

    File("../../provider-integration/integration-module/src/nativeMain/kotlin/Serializers.kt")
        .writeText(serializerBuilder.toString())
}

private fun expectedJsonElementFromRef(type: GeneratedTypeReference): String {
    return when (type) {
        is GeneratedTypeReference.Any -> "TODO()"
        is GeneratedTypeReference.Array -> "JsonStreamElement.ArrayStart"
        is GeneratedTypeReference.Bool -> "JsonStreamElement.Bool"
        is GeneratedTypeReference.Structure -> "JsonStreamElement.ObjectStart"
        is GeneratedTypeReference.Text -> "JsonStreamElement.Text"
        is GeneratedTypeReference.Dictionary -> "JsonStreamElement.ObjectStart"

        is GeneratedTypeReference.Float32,
        is GeneratedTypeReference.Float64,
        is GeneratedTypeReference.Int16,
        is GeneratedTypeReference.Int32,
        is GeneratedTypeReference.Int64,
        is GeneratedTypeReference.Int8 -> "JsonStreamElement.Number"

        else -> {
            error("Should not happen")
        }
    }
}

private fun buildParserForType(
    outVariable: String,
    builder: StringBuilder,
    type: GeneratedTypeReference
): Unit = with(builder) {
    if (type.originalIsNullable) {
        appendLine("if (prop.element is JsonStreamElement.Null) { /* do nothing */ }")
        append("else ")
    }
    append("if (prop.element is ")
    append(expectedJsonElementFromRef(type))
    appendLine(") {")
    when (type) {
        is GeneratedTypeReference.Any -> "TODO()"
        is GeneratedTypeReference.Array -> {
            val listVariable = outVariable + "List"
            append("val $listVariable = ArrayList<")
            append(type.valueType.kotlinType())
            appendLine(">()")
            appendLine("while (true) {")
            appendLine("val tok = parser.nextToken()")
            appendLine("if (tok == JsonStreamElement.ArrayEnd) {")
            appendLine("break")
            append("} else if (tok is ")
            append(expectedJsonElementFromRef(type))
            appendLine(") {")
            val elemVar = outVariable + "Elem"
            appendLine("val $elemVar: ${type.valueType.kotlinType()}")

            buildParserForType(elemVar, builder, type.valueType)
            appendLine("$listVariable.add($elemVar)")
            appendLine("} else { throw ParsingException(\"Wrong element type for $outVariable\") }")
            appendLine("}")
            appendLine("$outVariable = $listVariable")
        }

        is GeneratedTypeReference.Structure -> {
            appendLine("$outVariable = parseJson<${type.kotlinType()}>(parser)")
        }

        is GeneratedTypeReference.Dictionary -> "TODO()"

        is GeneratedTypeReference.Bool -> {
            appendLine("${outVariable} = prop.element.value")
        }

        is GeneratedTypeReference.Text -> {
            appendLine("${outVariable} = prop.element.value")
        }

        is GeneratedTypeReference.ConstantString,
        is GeneratedTypeReference.Void -> error("Should not happen")


        is GeneratedTypeReference.Float32 -> {
            appendLine("${outVariable} = prop.element.value.toFloat()")
        }

        is GeneratedTypeReference.Float64 -> {
            appendLine("${outVariable} = prop.element.value")
        }

        is GeneratedTypeReference.Int16 -> {
            appendLine("${outVariable} = prop.element.value.toShort()")
        }

        is GeneratedTypeReference.Int32 -> {
            appendLine("${outVariable} = prop.element.value.toInt()")
        }

        is GeneratedTypeReference.Int64 -> {
            appendLine("${outVariable} = prop.element.value.toLong()")
        }

        is GeneratedTypeReference.Int8 -> {
            appendLine("${outVariable} = prop.element.value.toInt().toByte()")
        }
    }
    appendLine("} else { throw ParsingException(\"Wrong type for ${outVariable}\") }")
}

private fun shouldSkip(type: GeneratedTypeReference): Boolean {
    if (type is GeneratedTypeReference.Void || type is GeneratedTypeReference.ConstantString) return true
    if (!type.fromConstructor) return true
    return false
}

fun generateKotlinDeserializer(
    builder: StringBuilder,
    qualifiedName: String,
    type: GeneratedType.Struct
): Unit = with(builder) {
    append("inline fun ")
    if (type.generics.isNotEmpty()) {
        append("<")
        for ((index, generic) in type.generics.withIndex()) {
            if (index != 0) append(", ")
            append("reified ")
            append(generic)
        }
        append("> ")
    }
    append("`${qualifiedName.replace('.', '_')}_deserialize`(parser: JsonParser, objectHasStarted: Boolean = false): $qualifiedName")
    if (type.generics.isNotEmpty()) {
        append("<")
        for ((index, generic) in type.generics.withIndex()) {
            if (index != 0) append(", ")
            append(generic)
        }
        append(">")
    }
    appendLine(" {")

    for (prop in type.properties) {
        if (shouldSkip(prop.type)) continue
        append("var ${prop.name}: ${prop.type.kotlinType()}")
        if (!prop.type.originalIsNullable) {
            append("?")
        }
        appendLine(" = null")
    }
    appendLine("if (!objectHasStarted && parser.nextToken() != JsonStreamElement.ObjectStart) throw ParsingException(\"Expected an object\")")

    appendLine("while (true) {")
    appendLine("val nextParserToken = parser.nextToken()")
    appendLine("if (nextParserToken == JsonStreamElement.ObjectEnd) break")
    appendLine("val prop = nextParserToken as? JsonStreamElement.Property ?: throw ParsingException(\"Expected property but got \$nextParserToken\")")
    appendLine("when (prop.key) {")
    for (prop in type.properties) {
        if (shouldSkip(prop.type)) continue
        appendLine("\"${prop.name}\" -> {")
        buildParserForType(prop.name, builder, prop.type)
        appendLine("}")
    }
    appendLine("else -> {")
    appendLine("if (prop.element == JsonStreamElement.ArrayStart || prop.element == JsonStreamElement.ObjectStart) { parser.skipCurrentContext() }")
    appendLine("}")

    appendLine("}")
    appendLine("}")

    appendLine("return $qualifiedName(")
    for (prop in type.properties) {
        if (shouldSkip(prop.type)) continue

        if (prop.type.originalIsNullable) {
            appendLine("${prop.name} = ${prop.name},")
        } else {
            appendLine("${prop.name} = ${prop.name} ?: throw ParsingException(\"Missing key '${prop.name}'\"),")
        }
    }
    appendLine(")")

    appendLine("}")
}

private fun GeneratedTypeReference.kotlinType(): String {
    val baseValue: String = when (this) {
        is GeneratedTypeReference.Any -> "Any"
        is GeneratedTypeReference.Array -> "List<${valueType.kotlinType()}>"
        is GeneratedTypeReference.Bool -> "Boolean"
        is GeneratedTypeReference.ConstantString -> "String"
        is GeneratedTypeReference.Dictionary -> "kotlinx.serialization.json.JsonObject"
        is GeneratedTypeReference.Float32 -> "Float"
        is GeneratedTypeReference.Float64 -> "Double"
        is GeneratedTypeReference.Int16 -> "Short"
        is GeneratedTypeReference.Int32 -> "Int"
        is GeneratedTypeReference.Int64 -> "Long"
        is GeneratedTypeReference.Int8 -> "Byte"
        is GeneratedTypeReference.Structure -> buildString {
            append(name)
            if (generics.isNotEmpty()) {
                append("<")
                for ((index, generic) in generics.withIndex()) {
                    if (index != 0) append(", ")
                    append(generic.kotlinType())
                }
                append(">")
            }
        }
        is GeneratedTypeReference.Text -> "String"
        is GeneratedTypeReference.Void -> "Unit"
    }

    return if (originalIsNullable) "$baseValue?" else baseValue
}