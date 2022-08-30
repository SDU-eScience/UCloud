package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.calls.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.lang.reflect.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.superclasses
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField

sealed class GeneratedTypeReference {
    abstract var nullable: Boolean
    abstract var originalHasDefault: Boolean
    abstract var originalIsNullable: Boolean
    abstract var fromConstructor: Boolean

    data class Int8(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Int16(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Int32(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Int64(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Float32(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Float64(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Bool(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Text(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Structure(
        val name: String,
        val generics: List<GeneratedTypeReference> = emptyList(),
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Dictionary(
        val valueType: GeneratedTypeReference,
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Array(
        val valueType: GeneratedTypeReference,
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class ConstantString(val value: String) : GeneratedTypeReference() {
        override var nullable: Boolean = false
            set(value) {
                field = false
            }
        override var originalHasDefault: Boolean = false
        override var originalIsNullable: Boolean = false
        override var fromConstructor: Boolean = false
    }

    data class Void(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()

    data class Any(
        override var nullable: Boolean = false,
        override var originalHasDefault: Boolean = false,
        override var originalIsNullable: Boolean = false,
        override var fromConstructor: Boolean = false,
    ) : GeneratedTypeReference()
}

fun GeneratedTypeReference.packageNameOrNull(): String? {
    return when (this) {
        is GeneratedTypeReference.Structure -> {
            name.split(".").filter { it.firstOrNull()?.isUpperCase() == false }.joinToString(".")
        }

        else -> null
    }
}


data class Documentation(
    val deprecated: Boolean,
    val maturity: UCloudApiMaturity,
    val synopsis: String?,
    val description: String?,
    val importance: Int = 0,
)

sealed class GeneratedType {
    abstract val name: String
    abstract val doc: Documentation
    abstract val hasExplicitOwner: Boolean
    abstract var owner: KClass<out CallDescriptionContainer>?

    data class Enum(
        override val name: String,
        override val doc: Documentation,
        val options: List<EnumOption>,
        override val hasExplicitOwner: Boolean,
        override var owner: KClass<out CallDescriptionContainer>?,
    ) : GeneratedType()

    data class EnumOption(
        val name: String,
        val doc: Documentation
    )

    data class TaggedUnion(
        override val name: String,
        override val doc: Documentation,
        var baseProperties: List<Property>,
        val generics: List<String>,
        val options: List<GeneratedTypeReference>,
        override val hasExplicitOwner: Boolean,
        override var owner: KClass<out CallDescriptionContainer>?,
    ) : GeneratedType()

    data class Struct(
        override val name: String,
        override val doc: Documentation,
        var properties: List<Property>,
        val generics: List<String>,
        override val hasExplicitOwner: Boolean,
        override var owner: KClass<out CallDescriptionContainer>?,
    ) : GeneratedType()

    data class Property(
        val name: String,
        val doc: Documentation,
        val type: GeneratedTypeReference,
    )
}

@OptIn(ExperimentalStdlibApi::class)
fun traverseType(type: Type, visitedTypes: LinkedHashMap<String, GeneratedType>): GeneratedTypeReference {
    when (type) {
        is GenericArrayType -> {
            return GeneratedTypeReference.Array(traverseType(type.genericComponentType, visitedTypes))
        }

        is ParameterizedType -> {
            when {
                type.rawType == List::class.java || type.rawType == Set::class.java -> {
                    return GeneratedTypeReference.Array(traverseType(type.actualTypeArguments.first(), visitedTypes))
                }

                type.rawType == Map::class.java -> {
                    return GeneratedTypeReference.Dictionary(traverseType(type.actualTypeArguments[1], visitedTypes))
                }

                else -> {
                    val rawType = type.rawType
                    if (rawType !is Class<*>) {
                        TODO("Not yet implemented: $type is not a class")
                    }

                    val initialType = traverseType(type.rawType, visitedTypes)
                    if (initialType !is GeneratedTypeReference.Structure) error("Expected raw type to be a struct")
                    val qualifiedName = (type.rawType as Class<*>).canonicalName

                    val actualTypeArgs = type.actualTypeArguments.map {
                        traverseType(it, visitedTypes)
                    }

                    return GeneratedTypeReference.Structure(qualifiedName, actualTypeArgs)
                }
            }
        }

        is TypeVariable<*> -> {
            return GeneratedTypeReference.Structure(type.name)
        }

        is WildcardType -> {
            // This is probably a huge oversimplification
            return traverseType(type.upperBounds.firstOrNull() ?: Unit::class.java, visitedTypes)
        }

        JsonElement::class.java -> {
            return GeneratedTypeReference.Any(nullable = true)
        }

        JsonObject::class.java -> {
            return GeneratedTypeReference.Dictionary(GeneratedTypeReference.Any(nullable = true))
        }

        java.lang.Byte::class.java, Byte::class.java -> {
            return GeneratedTypeReference.Int8()
        }

        java.lang.Short::class.java, Short::class.java -> {
            return GeneratedTypeReference.Int16()
        }

        Integer::class.java, Int::class.java -> {
            return GeneratedTypeReference.Int32()
        }

        java.lang.Long::class.java, Long::class.java, BigInteger::class.java -> {
            return GeneratedTypeReference.Int64()
        }

        java.lang.Float::class.java, Float::class.java -> {
            return GeneratedTypeReference.Float32()
        }

        java.lang.Double::class.java, Double::class.java, BigDecimal::class.java -> {
            return GeneratedTypeReference.Float64()
        }

        String::class.java -> {
            return GeneratedTypeReference.Text()
        }

        java.lang.Boolean::class.java, Boolean::class.java -> {
            return GeneratedTypeReference.Bool()
        }

        java.lang.Void::class.java -> {
            return GeneratedTypeReference.Void()
        }

        is Class<*> -> {
            val qualifiedName = type.canonicalName
            val existing = visitedTypes[qualifiedName]
            if (existing != null) return GeneratedTypeReference.Structure(qualifiedName)
            val doc = type.documentation()
            val owner = type.findAnnotation<UCloudApiOwnedBy>()?.owner

            if (type.isArray) {
                val componentType = traverseType(type.componentType, visitedTypes)
                return GeneratedTypeReference.Array(componentType)
            }

            if (type.isEnum) {
                visitedTypes[qualifiedName] = GeneratedType.Enum(
                    qualifiedName,
                    doc,
                    type.enumConstants.map {
                        val name = (it as Enum<*>).name
                        val field = type.getField(name)
                        GeneratedType.EnumOption(name, field.documentation(type.packageName, doc.maturity))
                    },
                    owner != null,
                    owner
                )

                return GeneratedTypeReference.Structure(qualifiedName)
            }

            if (type == Unit::class.java) {
                return GeneratedTypeReference.Void()
            }

            if (type == Any::class.java) {
                return GeneratedTypeReference.Any()
            }

            // Immediately put something in the visitedTypes to avoid infinite recursion. We update this value later,
            // so it doesn't have to be correct.
            visitedTypes[qualifiedName] = GeneratedType.Struct(
                qualifiedName, doc, emptyList(), emptyList(),
                owner != null, owner
            )

            val properties = ArrayList<GeneratedType.Property>()
            val generics = ArrayList<String>()

            if (type.isKotlinClass()) {
                val kotlinType = type.kotlin

                kotlinType.typeParameters.forEach { typeParam ->
                    generics.add(typeParam.name)
                }

                kotlinType.primaryConstructor?.parameters?.forEach { prop ->
                    if (prop.name == null) return@forEach

                    val classProp = kotlinType.memberProperties.find { it.name == prop.name }
                    val classPropAnnotations = classProp?.annotations ?: emptyList()
                    val javaFieldAnnotations = (classProp?.javaField?.annotations?.toList() ?: emptyList())
                    val getterAnnotations = classProp?.getter?.annotations ?: emptyList()
                    val parentProp = kotlinType.superclasses
                        .mapNotNull { it.memberProperties.find { it.name == prop.name } }
                        .firstOrNull()

                    val parentPropAnnotations = parentProp?.annotations ?: emptyList()
                    val parentJavaAnnotations = parentProp?.javaField?.annotations?.toList() ?: emptyList()
                    val parentGetterAnnotations = parentProp?.getter?.annotations ?: emptyList()
                    val annotations: Set<Annotation> =
                        (prop.annotations + javaFieldAnnotations + getterAnnotations + classPropAnnotations +
                            parentPropAnnotations + parentJavaAnnotations + parentGetterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore || it is Transient }) return@forEach

                    val propType = traverseType(prop.type.javaType, visitedTypes)

                    val (synopsis, description, importance) =
                        annotations.filterIsInstance<UCloudApiDoc>().firstOrNull().split(propType.packageNameOrNull())

                    var propName = prop.name!!
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
                    }

                    val serialNameAnnotation = annotations.filterIsInstance<SerialName>().firstOrNull()
                    if (serialNameAnnotation != null) {
                        propName = serialNameAnnotation.value
                    }

                    val deprecated = annotations.any { it is Deprecated }
                    val nullable = prop.type.isMarkedNullable || prop.isOptional

                    val maturity = run {
                        val internalMaturity = annotations.filterIsInstance<UCloudApiInternal>().firstOrNull()
                        val experimentalMaturity = annotations.filterIsInstance<UCloudApiExperimental>().firstOrNull()
                        val stableMaturity = annotations.filterIsInstance<UCloudApiStable>().firstOrNull()

                        when {
                            stableMaturity != null -> UCloudApiMaturity.Stable
                            experimentalMaturity != null -> UCloudApiMaturity.Experimental(experimentalMaturity.level)
                            internalMaturity != null -> UCloudApiMaturity.Internal(internalMaturity.level)
                            else -> doc.maturity
                        }
                    }

                    properties.add(GeneratedType.Property(
                        propName,
                        Documentation(deprecated, maturity, synopsis?.trim(), description?.trim(), importance),
                        propType.also {
                            it.nullable = nullable
                            it.originalIsNullable = prop.type.isMarkedNullable
                            it.originalHasDefault = prop.isOptional
                            it.fromConstructor = true
                        }
                    ))
                }

                // Almost the identical code for the properties which are not part of the primary constructor.
                // The code is unfortunately not easily refactored due to slightly different types.
                kotlinType.memberProperties.forEach { prop ->
                    if (properties.any { it.name == prop.name }) return@forEach

                    val javaFieldAnnotations = prop.javaField?.annotations?.toList() ?: emptyList()
                    val getterAnnotations = prop.getter.annotations
                    val annotations: Set<Annotation> =
                        (prop.annotations + javaFieldAnnotations + getterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore || it is Transient }) return@forEach

                    val propType = traverseType(prop.returnType.javaType, visitedTypes)

                    val (synopsis, description, importance) =
                        annotations.filterIsInstance<UCloudApiDoc>().firstOrNull().split(propType.packageNameOrNull())

                    var propName = prop.name
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
                    }

                    val serialNameAnnotation = annotations.filterIsInstance<SerialName>().firstOrNull()
                    if (serialNameAnnotation != null) {
                        propName = serialNameAnnotation.value
                    }

                    val deprecated = annotations.any { it is Deprecated }
                    val nullable = prop.returnType.isMarkedNullable
                    val maturity = run {
                        val internalMaturity = annotations.filterIsInstance<UCloudApiInternal>().firstOrNull()
                        val experimentalMaturity = annotations.filterIsInstance<UCloudApiExperimental>().firstOrNull()
                        val stableMaturity = annotations.filterIsInstance<UCloudApiStable>().firstOrNull()

                        when {
                            stableMaturity != null -> UCloudApiMaturity.Stable
                            experimentalMaturity != null -> UCloudApiMaturity.Experimental(experimentalMaturity.level)
                            internalMaturity != null -> UCloudApiMaturity.Internal(internalMaturity.level)
                            else -> UCloudApiMaturity.Internal(UCloudApiMaturity.Internal.Level.BETA)
                        }
                    }

                    properties.add(GeneratedType.Property(
                        propName,
                        Documentation(deprecated, maturity, synopsis?.trim(), description?.trim(), importance),
                        propType.also { it.nullable = nullable }
                    ))
                }

                val serialName = kotlinType.findAnnotation<SerialName>()
                if (serialName != null) {
                    properties.add(
                        GeneratedType.Property(
                            "type",
                            Documentation(false, UCloudApiMaturity.Stable, "The type discriminator", null),
                            GeneratedTypeReference.ConstantString(serialName.value)
                        )
                    )
                }

                if (kotlinType.isSealed) {
                    val options = kotlinType.sealedSubclasses.map {
                        traverseType(it.java, visitedTypes)
                    }

                    visitedTypes[qualifiedName] = GeneratedType.TaggedUnion(
                        qualifiedName, doc, properties,
                        generics, options, owner != null, owner
                    )

                    return GeneratedTypeReference.Structure(qualifiedName)
                }

                visitedTypes[qualifiedName] = GeneratedType.Struct(
                    qualifiedName, doc, properties, generics,
                    owner != null, owner
                )
                return GeneratedTypeReference.Structure(qualifiedName)
            } else {
                TODO("Non-primitive and non-kotlin class $type ${type::class}")
            }
        }

        else -> {
            error("Unknown thing: $type")
        }
    }
}

inline fun <reified T : Annotation> Class<*>.findAnnotation(): T? {
    val kotlinType = if (isKotlinClass()) kotlin else null
    val kotlinAnnotation = (listOfNotNull(kotlinType) + (kotlinType?.supertypes ?: emptyList())
        .mapNotNull { it.classifier as? KClass<*> })
        .mapNotNull { it.findAnnotation<T>() }
        .firstOrNull()

    if (kotlinAnnotation != null) {
        return kotlinAnnotation
    }

    val jvmAnnotation = this.annotations.filterIsInstance<T>().firstOrNull()
    if (jvmAnnotation != null) {
        return jvmAnnotation
    }

    return null
}

fun Class<*>.documentation(): Documentation {
    val type = this
    val (synopsis, description, importance) = type.findAnnotation<UCloudApiDoc>().split(packageName)
    val deprecated = type.findAnnotation<Deprecated>() != null
    val maturity = run {
        val internalMaturity = type.findAnnotation<UCloudApiInternal>()
        val experimentalMaturity = type.findAnnotation<UCloudApiExperimental>()
        val stableMaturity = type.findAnnotation<UCloudApiStable>()

        when {
            stableMaturity != null -> UCloudApiMaturity.Stable
            experimentalMaturity != null -> UCloudApiMaturity.Experimental(experimentalMaturity.level)
            internalMaturity != null -> UCloudApiMaturity.Internal(internalMaturity.level)
            else -> UCloudApiMaturity.Internal(UCloudApiMaturity.Internal.Level.BETA)
        }
    }

    return Documentation(deprecated, maturity, synopsis?.trim(), description?.trim(), importance)
}

fun Field.documentation(currentPackage: String, defaultMaturity: UCloudApiMaturity): Documentation {
    val (synopsis, description, importance) = annotations.filterIsInstance<UCloudApiDoc>()
        .firstOrNull().split(currentPackage)
    val deprecated = annotations.filterIsInstance<Deprecated>().firstOrNull() != null
    val maturity = run {
        val internalMaturity = annotations.filterIsInstance<UCloudApiInternal>().firstOrNull()
        val experimentalMaturity = annotations.filterIsInstance<UCloudApiExperimental>().firstOrNull()
        val stableMaturity = annotations.filterIsInstance<UCloudApiStable>().firstOrNull()

        when {
            stableMaturity != null -> UCloudApiMaturity.Stable
            experimentalMaturity != null -> UCloudApiMaturity.Experimental(experimentalMaturity.level)
            internalMaturity != null -> UCloudApiMaturity.Internal(internalMaturity.level)
            else -> defaultMaturity
        }
    }

    return Documentation(deprecated, maturity, synopsis?.trim(), description?.trim(), importance)
}

data class SynopsisAndDescription(val synopsis: String?, val description: String?, val importance: Int)

fun UCloudApiDoc?.split(currentPackage: String?): SynopsisAndDescription {
    if (this == null) return SynopsisAndDescription(null, null, 0)
    val normalized = documentation.trimIndent()
    return SynopsisAndDescription(
        processDocumentation(currentPackage, normalized.substringBefore('\n')),
        processDocumentation(currentPackage, normalized.substringAfter('\n', "").trim()).takeIf { it.isNotEmpty() },
        importance,
    )
}

fun UCloudApiDocC?.split(currentPackage: String?): SynopsisAndDescription {
    if (this == null) return SynopsisAndDescription(null, null, 0)
    val normalized = documentation.trimIndent()
    return SynopsisAndDescription(
        processDocumentation(currentPackage, normalized.substringBefore('\n')),
        processDocumentation(currentPackage, normalized.substringAfter('\n', "").trim()).takeIf { it.isNotEmpty() },
        importance,
    )
}

fun processDocumentation(currentPackage: String?, docString: String): String {
    var phase = 0
    var docString = docString
    var isRunning = true
    while (isRunning) {
        fun replaceTags(
            token: String,
            replacement: (String) -> String
        ) {
            docString = buildString {
                var cursor = 0
                while (cursor < docString.length) {
                    val nextTypeRef = docString.indexOf(token, cursor)
                    if (nextTypeRef == -1) {
                        append(docString.substring(cursor))
                        cursor = docString.length
                    } else {
                        append(docString.substring(cursor, nextTypeRef))
                        val refStartIdx = nextTypeRef + token.length + 1
                        if (refStartIdx >= docString.length) {
                            throw IllegalStateException("Unable to parse documentation in $currentPackage:\n${docString}")
                        }

                        var (refEndOfToken, addWhitespace) = docString.findEndOfIdentifier(refStartIdx)
                        if (refEndOfToken == -1) refEndOfToken = docString.length
                        val token = docString.substring(refStartIdx, refEndOfToken).trim()
                        cursor = refEndOfToken

                        append(replacement(token))
                        if (addWhitespace) append(' ')
                    }
                }
            }
        }


        when (phase) {
            0 -> {
                replaceTags(TYPE_REF) { token ->
                    if (currentPackage == null) {
                        "`${token}`"
                    } else {
                        val qualifiedName = if (!token.startsWith("dk.sdu.cloud.")) {
                            "$currentPackage.$token"
                        } else {
                            token
                        }

                        "[`${simplifyName(token)}`](/docs/reference/${qualifiedName}.md)"
                    }
                }
            }

            1 -> {
                replaceTags(CALL_REF) { token ->
                    "[`${token}`](/docs/reference/${token}.md)"
                }
            }

            2 -> {
                replaceTags(TYPE_REF_LINK) { token ->
                    if (currentPackage == null) {
                        "#"
                    } else {
                        val qualifiedName = if (!token.startsWith("dk.sdu.cloud.")) {
                            "$currentPackage.$token"
                        } else {
                            token
                        }

                        "/docs/reference/${qualifiedName}.md"
                    }
                }
            }

            3 -> {
                replaceTags(CALL_REF_LINK) { token ->
                    "/docs/reference/${token}.md"
                }
            }

            else -> {
                isRunning = false
            }
        }

        phase++
    }

    return docString
}

private fun CharSequence.findEndOfIdentifier(startIndex: Int): Pair<Int, Boolean> {
    var cursor = startIndex
    val stringLength = length
    while (cursor < stringLength) {
        val nextChar = get(cursor++)
        if (nextChar == '\n' || nextChar.isWhitespace()) {
            val peek1 = getOrNull(cursor)
            val peek2 = getOrNull(cursor + 1)
            if (peek1 == 's' || peek1 == 'S' || peek1 == '.') {
                if (peek2 == null || !peek2.isLetterOrDigit()) {
                    return Pair(cursor, false)
                }
            }
            return Pair(cursor - 1, true)
        }
        if (nextChar != '.' && !nextChar.isJavaIdentifierPart()) {
            return Pair(cursor - 1, false)
        }
    }
    return Pair(-1, false)
}
