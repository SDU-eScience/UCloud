package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.calls.UCloudApiDoc
import dk.sdu.cloud.calls.UCloudApiExperimental
import dk.sdu.cloud.calls.UCloudApiInternal
import dk.sdu.cloud.calls.UCloudApiMaturity
import dk.sdu.cloud.calls.UCloudApiStable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.superclasses
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmName

sealed class GeneratedTypeReference {
    abstract var nullable: Boolean

    data class Int8(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Int16(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Int32(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Int64(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Float32(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Float64(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Bool(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Text(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Structure(
        val name: String,
        val generics: List<GeneratedTypeReference> = emptyList(),
        override var nullable: Boolean = false
    ) : GeneratedTypeReference()

    data class Dictionary(
        val valueType: GeneratedTypeReference,
        override var nullable: Boolean = false
    ) : GeneratedTypeReference()

    data class Array(
        val valueType: GeneratedTypeReference,
        override var nullable: Boolean = false
    ) : GeneratedTypeReference()

    data class ConstantString(val value: String) : GeneratedTypeReference() {
        override var nullable: Boolean = false
            set(value) {
                error("Cannot change the nullability of a constant string")
            }
    }

    data class Void(override var nullable: Boolean = false) : GeneratedTypeReference()
    data class Any(override var nullable: Boolean = false) : GeneratedTypeReference()
}

data class ResponseExample(val statusCode: Int, val description: String)
data class UseCaseReference(val usecase: String, val description: String)

data class GeneratedRemoteProcedureCall(
    val requestType: GeneratedTypeReference,
    val responseType: GeneratedTypeReference,
    val errorType: GeneratedTypeReference,
    val namespace: String,
    val name: String,
    val roles: Set<Role>,
    val responseExamples: List<ResponseExample>,
    val useCaseReferences: List<UseCaseReference>,
    val doc: Documentation,
)

data class Documentation(
    val deprecated: Boolean,
    val maturity: UCloudApiMaturity,
    val synopsis: String?,
    val description: String?
)

sealed class GeneratedType {
    abstract val name: String
    abstract val doc: Documentation

    data class Enum(
        override val name: String,
        override val doc: Documentation,
        val options: List<EnumOption>
    ) : GeneratedType()

    data class EnumOption(
        val name: String,
        val doc: Documentation
    )

    data class TaggedUnion(
        override val name: String,
        override val doc: Documentation,
        val baseProperties: List<Property>,
        val generics: List<String>,
        val options: List<GeneratedTypeReference>
    ) : GeneratedType()

    data class Struct(
        override val name: String,
        override val doc: Documentation,
        val properties: List<Property>,
        val generics: List<String>,
    ) : GeneratedType()

    data class Property(
        val name: String,
        val doc: Documentation,
        val type: GeneratedTypeReference,
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun traverseType(type: Type, visitedTypes: LinkedHashMap<String, GeneratedType>): GeneratedTypeReference {
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
                    val rawComputedType = initialType.copy(properties = HashMap(initialType.properties))

                    val actualTypeArgs = type.actualTypeArguments.map { traverseType(it, visitedTypes) }
                    val typeParams = rawType.typeParameters

                    for (entry in rawComputedType.properties.entries) {
                        val value = entry.value
                        if (value is ComputedType.Array && value.itemType is ComputedType.Generic) {
                            val typeIdx = typeParams.indexOfFirst { it.name == value.itemType.id }
                            if (typeIdx == -1) error("type idx is -1")
                            val newValue = actualTypeArgs[typeIdx].asRef()
                            entry.setValue(ComputedType.Array(newValue).apply {
                                documentation = entry.value.documentation
                                nullable = entry.value.nullable
                                optional = entry.value.optional
                                deprecated = entry.value.deprecated
                            })
                        } else if (value is ComputedType.Generic) {
                            val typeIdx = typeParams.indexOfFirst { it.name == value.id }
                            if (typeIdx == -1) error("type idx is -1")
                            val newValue = actualTypeArgs[typeIdx].asRef()
                            entry.setValue(newValue)
                        } else if (value is ComputedType.Dictionary && value.itemType is ComputedType.Generic) {
                            val typeIdx = typeParams.indexOfFirst { it.name == value.itemType.id }
                            if (typeIdx == -1) error("type idx is -1")
                            val newValue = actualTypeArgs[typeIdx].asRef()
                            entry.setValue(ComputedType.Dictionary(newValue).apply {
                                documentation = entry.value.documentation
                                nullable = entry.value.nullable
                                optional = entry.value.optional
                                deprecated = entry.value.deprecated
                            })
                        }
                    }

                    return rawComputedType
                }
            }
        }
        /*

        is TypeVariable<*> -> {
            return ComputedType.Generic(type.name)
        }

        is WildcardType -> {
            // This is probably a huge oversimplification
            return traverseType(type.upperBounds.firstOrNull() ?: Unit::class.java, visitedTypes)
        }

        */

        JsonObject::class.java -> {
            return GeneratedTypeReference.Dictionary(GeneratedTypeReference.Any(nullable = true))
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

            if (type.isEnum) {
                visitedTypes[qualifiedName] = GeneratedType.Enum(
                    qualifiedName,
                    doc,
                    type.enumConstants.map {
                        val name = (it as Enum<*>).name
                        val field = type.getField(name)!!
                        GeneratedType.EnumOption(name, field.documentation())
                    }
                )

                return GeneratedTypeReference.Structure(qualifiedName)
            }

            if (type == Unit::class.java || type == Any::class.java) {
                return GeneratedTypeReference.Void()
            }

            val properties = ArrayList<GeneratedType.Property>()
            val generics = ArrayList<String>()
            val struct = GeneratedType.Struct(qualifiedName, doc, properties)
            visitedTypes[qualifiedName] = struct

            if (type.isKotlinClass()) {
                val kotlinType = type.kotlin

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

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()?.documentation
                    val synopsis = propApiDoc?.substringBefore('\n')
                    val description = propApiDoc?.substringAfter('\n')

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
                            else -> UCloudApiMaturity.Internal(UCloudApiMaturity.Internal.Level.BETA)
                        }
                    }

                    properties.add(GeneratedType.Property(
                        propName,
                        Documentation(deprecated, maturity, synopsis, description),
                        propType.also { it.nullable = nullable }
                    ))
                }

                // Almost the identical code for the properties which are not part of the primary constructor.
                // The code is unfortunately not easily refactorable due to slightly different types.
                kotlinType.memberProperties.forEach { prop ->
                    if (properties.any { it.name == prop.name }) return@forEach

                    val javaFieldAnnotations = prop.javaField?.annotations?.toList() ?: emptyList()
                    val getterAnnotations = prop.getter?.annotations ?: emptyList()
                    val annotations: Set<Annotation> =
                        (prop.annotations + javaFieldAnnotations + getterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore || it is Transient }) return@forEach

                    val propType = traverseType(prop.returnType.javaType, visitedTypes)

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()?.documentation
                    val synopsis = propApiDoc?.substringBefore('\n')
                    val description = propApiDoc?.substringAfter('\n')

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
                        Documentation(deprecated, maturity, synopsis, description),
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
                    val options = kotlinType.sealedSubclasses.mapNotNull {
                        traverseType(it.java, visitedTypes)
                    }

                    visitedTypes[qualifiedName] = GeneratedType.TaggedUnion(qualifiedName, doc, properties,
                        generics, options)

                    return GeneratedTypeReference.Structure(qualifiedName)
                }

                visitedTypes[qualifiedName] = GeneratedType.Struct(qualifiedName, doc, properties, generics)
                return GeneratedTypeReference.Structure(qualifiedName)
            } else {
                TODO("Non-primitive and non-kotlin class $type")
            }
        }

        else -> {
            error("Unknown thing: $type")
        }
    }
    TODO()
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
    val documentation = type.findAnnotation<UCloudApiDoc>()?.documentation
    val synopsis = documentation?.split("\n")?.getOrNull(0)
    val description = documentation?.substringAfter('\n')
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

    return Documentation(deprecated, maturity, synopsis, description)
}

fun Field.documentation(): Documentation {
    val documentation = annotations.filterIsInstance<UCloudApiDoc>()?.firstOrNull()?.documentation
    val synopsis = documentation?.split("\n")?.getOrNull(0)
    val description = documentation?.substringAfter('\n')
    val deprecated = annotations.filterIsInstance<Deprecated>().firstOrNull() != null
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

    return Documentation(deprecated, maturity, synopsis, description)
}

fun runGenerator() {

}
