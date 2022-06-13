package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingRequestInterceptor
import dk.sdu.cloud.calls.server.OutgoingCallResponse
import dk.sdu.cloud.micro.PlaceholderServiceDescription
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.server
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.*
import kotlin.reflect.javaType
import kotlin.system.exitProcess

data class CallExtension(
    @JsonIgnore val call: CallDescription<*, *, *>,
    @JsonIgnore var requestType: ComputedType? = null,
    @JsonIgnore var responseType: ComputedType? = null,
) {
    companion object {
        const val EXTENSION = "x-ucloud-call"
    }
}

fun main() {
    runOpenApiGenerator()
}

@OptIn(ExperimentalStdlibApi::class)
fun runOpenApiGenerator(args: Array<String>? = null) {
    val reg = ServiceRegistry(args ?: arrayOf("--dev"), PlaceholderServiceDescription)
    val knownCalls = ArrayList<CallDescription<*, *, *>>()
    reg.rootMicro.server.attachRequestInterceptor(object : IngoingRequestInterceptor<HttpCall, HttpCall.Companion> {
        override val companion = HttpCall.Companion
        override fun addCallListenerForCall(call: CallDescription<*, *, *>) {
            knownCalls.add(call)
        }

        override suspend fun <R : Any> parseRequest(ctx: HttpCall, call: CallDescription<R, *, *>): R {
            error("Will not parse")
        }

        override suspend fun <R : Any, S : Any, E : Any> produceResponse(
            ctx: HttpCall,
            call: CallDescription<R, S, E>,
            callResult: OutgoingCallResponse<S, E>,
        ) {
            error("Will not respond")
        }
    })

    services.forEach { objectInstance ->
        try {
            Launcher.log.trace("Registering ${objectInstance.javaClass.canonicalName}")
            reg.register(objectInstance)
        } catch (ex: Throwable) {
            Launcher.log.error("Caught error: ${ex.stackTraceToString()}")
        }
    }

    reg.start(wait = false)

    val log = LoggerFactory.getLogger("dk.sdu.cloud.OpenApiGenerator")

    writeSpecification(
        knownCalls,
        File("/tmp/swagger/combined"),
        subtitle = "Full API",
        isFullApi = true,
    )

    exitProcess(0)
}


private fun writeSpecification(
    knownCalls: MutableList<CallDescription<*, *, *>>,
    output: File,
    subtitle: String? = null,
    documentation: String? = null,
    isFullApi: Boolean = false
) {
    val typeRegistry = LinkedHashMap<String, ComputedType>()
    knownCalls
        .groupBy { it.httpOrNull?.path?.toPath() ?: "" }
        .forEach { (path, calls) ->
            if (path == "") return@forEach

            for (call in calls) {
                val http = call.httpOrNull ?: continue
                val doc = call.docOrNull
                val matchingField = call.field

                if (matchingField == null) {
                    Launcher.log.warn("Could not find call $call")
                }

                val maturity = call.apiMaturityOrNull ?: UCloudApiMaturity.Internal(InternalLevel.BETA)
                var description = ""
                var summary = ""
                var deprecated = false
                var callId = ""

                description = """
                    ${apiMaturityBadge(maturity)}
                    ${rolesBadge(call.authDescription.roles)}
                    
                    
                """.trimIndent()
                if (doc != null) {
                    if (doc.description != null) description += doc.description
                    summary = doc.summary + " (${call.name})"
                    deprecated = matchingField?.hasAnnotation<Deprecated>() ?: false
                }

                callId = call.fullName
                if (callId.contains(".")) {
                    // Hack: Delete anything after '.' to allow versioning in call ids
                    callId = callId.substringBefore('.')
                }

                if (call.successClass.javaType != Unit::class) {
                    traverseType(call.successClass.javaType, typeRegistry)
                }

                val body = http.body
                if (body != null && !body.ref.descriptor.serialName.startsWith("kotlin.Unit")) {
                    traverseType(call.requestClass.javaType, typeRegistry)
                }

                val params = http.params
                if (params != null) {
                    traverseType(call.requestClass.javaType, typeRegistry) as? ComputedType.Struct
                        ?: error("Query params bound to a non-class ${call.fullName}")
                }
            }
        }

    println("UCloud has ${typeRegistry.size} number of types")
    output.mkdirs()
//    generateTypeScriptCode(output, doc, typeRegistry)
//    generateSpringMvcCode()
    // injectMarkdownDocs(doc, typeRegistry)
}

// Call tree traversal
sealed class ComputedType {
    var documentation: String? = null
    var deprecated: Boolean = false
    var nullable: Boolean = false
    var optional: Boolean = false

    class Integer(val size: Int) : ComputedType() {
        override fun toString(): String = "int$size"
    }

    class FloatingPoint(val size: Int) : ComputedType() {
        override fun toString() = "float$size"
    }

    class Text() : ComputedType() {
        override fun toString() = "text"
    }

    class Bool() : ComputedType() {
        override fun toString() = "bool"
    }

    class Array(val itemType: ComputedType) : ComputedType() {
        override fun toString() = "$itemType[]"
    }

    data class Dictionary(val itemType: ComputedType) : ComputedType() {
        override fun toString() = "dict_${itemType}_"
    }

    class Unknown : ComputedType() {
        override fun toString() = "unknown"
    }

    class Generic(val id: String) : ComputedType() {
        override fun toString() = "_${id}_"
    }

    data class Struct(
        var qualifiedName: String,
        var type: Type,
        var properties: MutableMap<String, ComputedType>,
        var discriminator: Discriminator? = null,
        var genericInfo: GenericInfo? = null,
        var tsDef: TSDefinition? = null,
        var parent: ComputedType.StructRef? = null,
    ) : ComputedType() {
        override fun toString() = qualifiedName

        data class GenericInfo(
            var baseType: String,
            var typeParameters: List<ComputedType>,
        )
    }

    class StructRef(val qualifiedName: String) : ComputedType()

    class Enum(val options: List<String>) : ComputedType() {
        override fun toString() = "enum_${options.joinToString("_")})"
    }

    data class Discriminator(
        val property: String,
        val valueToQualifiedName: Map<String, String>,
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun traverseType(type: Type, visitedTypes: LinkedHashMap<String, ComputedType>): ComputedType {
    TODO()
    /*
    when (type) {
        is GenericArrayType -> {
            return ComputedType.Array(traverseType(type.genericComponentType, visitedTypes))
        }

        is ParameterizedType -> {
            when {
                type.rawType == List::class.java || type.rawType == Set::class.java -> {
                    return ComputedType.Array(traverseType(type.actualTypeArguments.first(), visitedTypes))
                }

                type.rawType == Map::class.java -> {
                    return ComputedType.Dictionary(traverseType(type.actualTypeArguments[1], visitedTypes))
                }

                else -> {
                    val rawType = type.rawType
                    if (rawType !is Class<*>) {
                        TODO("Not yet implemented: $type is not a class")
                    }

                    val initialType = traverseType(type.rawType, visitedTypes)
                    if (initialType !is ComputedType.Struct) error("Expected raw type to be a struct")
                    val rawComputedType = initialType.copy(properties = HashMap(initialType.properties))

                    val actualTypeArgs = type.actualTypeArguments.map { traverseType(it, visitedTypes) }
                    val typeParams = rawType.typeParameters

                    rawComputedType.genericInfo = ComputedType.Struct.GenericInfo(
                        rawComputedType.qualifiedName,
                        actualTypeArgs
                    )

                    rawComputedType.qualifiedName = "${rawComputedType}_" +
                        "${actualTypeArgs.joinToString(",") { it.toString().replace(".", "_") }}_ "
                    visitedTypes[rawComputedType.qualifiedName] = rawComputedType

                    val nullableType = rawComputedType.copy()
                    nullableType.nullable = true
                    visitedTypes[nullableType.qualifiedName + "_Opt"] = nullableType

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

        is TypeVariable<*> -> {
            return ComputedType.Generic(type.name)
        }

        is WildcardType -> {
            // This is probably a huge oversimplification
            return traverseType(type.upperBounds.firstOrNull() ?: Unit::class.java, visitedTypes)
        }

        JsonObject::class.java -> {
            return ComputedType.Dictionary(ComputedType.Unknown())
        }

        java.lang.Short::class.java, Short::class.java -> {
            return ComputedType.Integer(16)
        }

        Integer::class.java, Int::class.java -> {
            return ComputedType.Integer(32)
        }

        java.lang.Long::class.java, Long::class.java, BigInteger::class.java -> {
            return ComputedType.Integer(64)
        }

        java.lang.Float::class.java, Float::class.java, BigDecimal::class.java -> {
            return ComputedType.FloatingPoint(32)
        }

        java.lang.Double::class.java, Double::class.java -> {
            return ComputedType.FloatingPoint(64)
        }

        String::class.java -> {
            return ComputedType.Text()
        }

        java.lang.Boolean::class.java, Boolean::class.java -> {
            return ComputedType.Bool()
        }

        java.lang.Void::class.java -> {
            return ComputedType.Unknown()
        }

        is Class<*> -> {
            if (type.isEnum) {
                return ComputedType.Enum(type.enumConstants.map { (it as Enum<*>).name })
            }

            if (type == Unit::class.java || type == Any::class.java) {
                return ComputedType.Unknown()
            }

            val qualifiedName = type.canonicalName
            val existing = visitedTypes[qualifiedName]
            if (existing != null) return existing

            val properties = LinkedHashMap<String, ComputedType>()
            val struct = ComputedType.Struct(qualifiedName, type, properties)
            visitedTypes[qualifiedName] = struct

            if (type.isKotlinClass()) {
                val kotlinType = type.kotlin

                struct.tsDef = kotlinType.findAnnotation<TSDefinition>()
                val apiDoc = (listOf(kotlinType) + kotlinType.supertypes.mapNotNull { it.classifier as? KClass<*> })
                    .mapNotNull { it.findAnnotation<UCloudApiDoc>() }.firstOrNull()
                if (apiDoc != null) {
                    struct.documentation = apiDoc.documentation
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
                            parentPropAnnotations+ parentJavaAnnotations + parentGetterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore || it is Transient }) return@forEach

                    val propType = traverseType(prop.type.javaType, visitedTypes).asRef()

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()
                    if (propApiDoc != null) {
                        propType.documentation = propApiDoc.documentation
                    }

                    var propName = prop.name!!
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
                    }

                    val serialNameAnnotation = annotations.filterIsInstance<SerialName>().firstOrNull()
                    if (serialNameAnnotation != null) {
                        propName = serialNameAnnotation.value
                    }

                    propType.deprecated = annotations.any { it is Deprecated }
                    propType.nullable = prop.type.isMarkedNullable
                    propType.optional = prop.isOptional

                    properties[propName] = propType
                }

                // Almost the identical code for the properties which are not part of the primary constructor.
                // The code is unfortunately not easily refactorable due to slightly different types.
                kotlinType.memberProperties.forEach { prop ->
                    if (prop.name in properties) return@forEach

                    val javaFieldAnnotations = prop.javaField?.annotations?.toList() ?: emptyList()
                    val getterAnnotations = prop.getter?.annotations ?: emptyList()
                    val annotations: Set<Annotation> =
                        (prop.annotations + javaFieldAnnotations + getterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore || it is Transient }) return@forEach

                    val propType = traverseType(prop.returnType.javaType, visitedTypes).asRef()

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()
                    if (propApiDoc != null) {
                        propType.documentation = propApiDoc.documentation
                    }

                    var propName = prop.name!!
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
                    }

                    val serialNameAnnotation = annotations.filterIsInstance<SerialName>().firstOrNull()
                    if (serialNameAnnotation != null) {
                        propName = serialNameAnnotation.value
                    }


                    propType.deprecated = annotations.any { it is Deprecated }
                    propType.nullable = prop.returnType.isMarkedNullable

                    properties[propName] = propType
                }

                val jsonTypeInfo = kotlinType.findAnnotation<JsonTypeInfo>()
                val jsonSubTypes = kotlinType.findAnnotation<JsonSubTypes>()
                if (jsonTypeInfo != null && jsonSubTypes != null) {
                    // We have a discriminator
                    struct.discriminator = ComputedType.Discriminator(
                        jsonTypeInfo.property,
                        jsonSubTypes.value.mapNotNull {
                            val subType = traverseType(it.value.java, visitedTypes)
                            if (subType !is ComputedType.Struct) return@mapNotNull null
                            it.name to subType.qualifiedName
                        }.toMap()
                    )
                }

                if (kotlinType.isSealed) {
                    struct.discriminator = ComputedType.Discriminator(
                        "type",
                        kotlinType.sealedSubclasses.mapNotNull {
                            val subType = traverseType(it.java, visitedTypes)
                            if (subType !is ComputedType.Struct) return@mapNotNull null
                            val serialName = it.findAnnotation<SerialName>()?.value ?: it.qualifiedName ?: it.jvmName
                            serialName to subType.qualifiedName
                        }.toMap()
                    )
                }

                val nullableStruct = struct.copy()
                nullableStruct.nullable = true
                visitedTypes[nullableStruct.qualifiedName + "_Opt"] = nullableStruct

                return struct
            } else {
                TODO("Non-primitive and non-kotlin class $type")
            }
        }

        else -> {
            error("Unknown thing: $type")
        }
    }
     */
}

val CallDescription<*, *, *>.field: KProperty1<CallDescriptionContainer, *>?
    get() {
        val call = this
        return call.containerRef.javaClass.kotlin.memberProperties.find {
            runCatching {
                val value = it.get(call.containerRef)
                val maybeCall = value as? CallDescription<*, *, *>
                maybeCall != null && maybeCall.name == call.name && maybeCall.namespace == call.namespace
            }.getOrElse { false }
        }
    }

val CallDescription<*, *, *>.apiMaturityOrNull: UCloudApiMaturity?
    get() {
        val callMaturity = findApiMaturity(field?.annotations ?: emptyList())
        if (callMaturity != null)  return callMaturity
        return findApiMaturity(containerRef.javaClass.kotlin.annotations)
    }

private fun findApiMaturity(annotations: List<Annotation>): UCloudApiMaturity? {
    annotations
        .singleOrNull {
            it is UCloudApiInternal ||
                it is UCloudApiExperimental ||
                it is UCloudApiStable
        }
        ?.let {
            return when (it) {
                is UCloudApiInternal -> UCloudApiMaturity.Internal(it.level)
                is UCloudApiExperimental -> UCloudApiMaturity.Experimental(it.level)
                is UCloudApiStable -> UCloudApiMaturity.Stable
                else -> null
            }
        }

    return null
}
