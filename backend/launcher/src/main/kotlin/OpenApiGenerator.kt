package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.accounting.api.Products
import dk.sdu.cloud.app.orchestrator.api.JobDescriptions
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingRequestInterceptor
import dk.sdu.cloud.calls.server.OutgoingCallResponse
import dk.sdu.cloud.micro.PlaceholderServiceDescription
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.server
import io.ktor.http.*
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.*
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.servers.Server
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField
import kotlin.system.exitProcess

private val componentsRef = "#/components/schemas/"

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val doc = OpenAPI().apply {
        servers = listOf(Server().url("https://cloud.sdu.dk"))
        info = Info().apply {
            version = "1.0.0"
            title = "UCloud"
            license = License().apply {
                name = "EUPL-1.2"
                url = "https://github.com/SDU-eScience/UCloud/blob/master/LICENSE.md"
            }
            addExtension("x-logo", mapOf("url" to "https://docs.cloud.sdu.dk/dev/_images/logo.png"))
            termsOfService = "https://legal.cloud.sdu.dk"
            contact = Contact().apply {
                email = "support@escience.sdu.dk"
            }
        }

        externalDocs = ExternalDocumentation().apply {
            url = "https://docs.cloud.sdu.dk"
        }

        // NOTE(Dan): Currently made to be rendered by https://github.com/Redocly/redoc

        val typeRegistry = HashMap<String, ComputedType>()

        val reg = ServiceRegistry(arrayOf("--dev"), PlaceholderServiceDescription)
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
                callResult: OutgoingCallResponse<S, E>
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

        paths = Paths()
        components = Components()

        knownCalls
            .groupBy { it.httpOrNull?.path?.toKtorTemplate(true) ?: "" }
            .forEach { (path, calls) ->
                if (path == "") return@forEach

                val pathItem = PathItem()
                paths[path] = pathItem

                for (call in calls) {
                    val http = call.httpOrNull ?: continue

                    val doc = call.docOrNull
                    val matchingField = call.containerRef.javaClass.kotlin.memberProperties.find {
                        runCatching {
                            val value = it.get(call.containerRef)
                            val maybeCall = value as? CallDescription<*, *, *>
                            maybeCall != null && maybeCall.name == call.name && maybeCall.namespace == call.namespace
                        }.getOrElse { false }
                    }

                    if (matchingField == null) {
                        Launcher.log.warn("Could not find call $call")
                    }

                    val op = Operation()
                    when (http.method) {
                        HttpMethod.Get -> pathItem.get = op
                        HttpMethod.Post -> pathItem.post = op
                        HttpMethod.Delete -> pathItem.delete = op
                        HttpMethod.Head -> pathItem.head = op
                        HttpMethod.Put -> pathItem.put = op
                        HttpMethod.Options -> pathItem.options = op
                        HttpMethod.Patch -> pathItem.patch = op
                    }

                    if (doc != null) {
                        op.description = doc.description
                        op.summary = doc.summary
                        op.deprecated = matchingField?.hasAnnotation<Deprecated>()
                    }

                    op.operationId = call.fullName
                    op.tags = listOf(call.namespace)
                    op.responses = ApiResponses().apply {
                        set("200", apiResponse(call.successType.type, typeRegistry).apply {
                            if (doc != null) {
                                val json = this.content[ContentType.Application.Json.toString()]
                                if (json != null) {
                                    json.examples = HashMap()
                                    for (example in doc.examples) {
                                        if (example.statusCode.isSuccess()) {
                                            json.examples[example.name] = createExample(example)
                                        }
                                    }
                                }
                            }
                        })

                        doc?.examples?.groupBy { it.statusCode }?.filterKeys { !it.isSuccess() }
                            ?.forEach { (code, examples) ->
                                set(code.value.toString(), apiResponse(call.errorType.type, typeRegistry).apply {
                                    val json = this.content[ContentType.Application.Json.toString()]
                                    if (json != null) {
                                        json.examples = HashMap()
                                        for (example in examples) {
                                            json.examples[example.name] = createExample(example)
                                        }
                                    }
                                })
                            }

                        doc?.errors?.forEach { err ->
                            val description = computeIfAbsent(
                                err.statusCode.value.toString(),
                                { apiResponse(call.errorType.type, typeRegistry) }
                            )

                            description.description = err.description
                        }

                        default = apiResponse(call.errorType.type, typeRegistry)
                    }

                    val body = http.body
                    if (body != null && body.ref.type != Unit::class.java) {
                        val requestType = traverseType(call.requestType.type, typeRegistry)
                        op.requestBody = RequestBody()
                        op.requestBody.content = Content().apply {
                            set(ContentType.Application.Json.toString(), MediaType().apply {
                                if (doc != null && body is HttpBody.BoundToEntireRequest<*>) {
                                    examples = HashMap()
                                    doc.examples.forEach { example ->
                                        examples[example.name] = createExample(example)
                                    }
                                }
                                schema = requestType.toOpenApiSchema()
                            })
                        }
                    }

                    val params = http.params
                    if (params != null) {
                        val requestType = traverseType(call.requestType.type, typeRegistry) as? ComputedType.Struct
                            ?: error("Query params bound to a non-class")

                        op.parameters = params.parameters.map { p ->
                            when (p) {
                                is HttpQueryParameter.Property<*, *> -> {
                                    val type = p.property.returnType.javaType

                                    Parameter().apply {
                                        name = p.property.name
                                        schema = requestType.properties[name]!!.toOpenApiSchema()
                                        `in` = "query"
                                    }
                                }
                            }
                        }
                    }
                }
            }

        components.schemas = typeRegistry.map { (k, v) ->
            (k) to v.toOpenApiSchema()
        }.toMap()
    }

    val output = File("/tmp/swagger").also { it.mkdirs() }
    File(output, "swagger.yaml").writeText(Yaml.pretty(doc))
    File(output, "swagger.json").writeText(Json.pretty(doc))
    exitProcess(0)
}

private fun createExample(example: UCloudCallDoc.Example<out Any, out Any, out Any>): Example {
    return Example().apply {
        summary = example.summary
        description = example.description
        value = example.response
    }
}

private fun apiResponse(type: Type, registry: HashMap<String, ComputedType>): ApiResponse {
    return ApiResponse().apply {
        val computedType = traverseType(type, registry)
        description = computedType.documentation
        if (type == Unit::class.java) {
            description = "No response"
        }

        content = Content().apply {
            set("application/json", MediaType().apply {
                schema = computedType.asRef().toOpenApiSchema()
            })
        }
    }
}

// Call tree traversal
sealed class ComputedType {
    var documentation: String? = null
    var deprecated: Boolean = false
    var nullable: Boolean = false
    var optional: Boolean = false

    protected fun baseSchema(): Schema<*> {
        return Schema<Any?>().apply {
            this.description = this@ComputedType.documentation
            this.deprecated = this@ComputedType.deprecated
            this.nullable = this@ComputedType.nullable
        }
    }

    abstract fun toOpenApiSchema(): Schema<*>

    open fun asRef(): ComputedType {
        return this
    }

    class Integer(val size: Int) : ComputedType() {
        override fun toString(): String = "int$size"

        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "integer"
                format = "int$size"
            }
        }
    }

    class FloatingPoint(val size: Int) : ComputedType() {
        override fun toString() = "float$size"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "number"
                format = "float$size"
            }
        }
    }

    class Text() : ComputedType() {
        override fun toString() = "text"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "string"
            }
        }
    }

    class Bool() : ComputedType() {
        override fun toString() = "bool"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "boolean"
            }
        }
    }

    class Array(val itemType: ComputedType) : ComputedType() {
        override fun toString() = "$itemType[]"

        override fun toOpenApiSchema(): Schema<*> {
            return ArraySchema().apply {
                type = "array"
                this.description = this@Array.documentation
                this.deprecated = this@Array.deprecated
                this.nullable = this@Array.nullable
                items = itemType.toOpenApiSchema()
            }
        }
    }

    data class Dictionary(val itemType: ComputedType) : ComputedType() {
        override fun toString() = "dict<$itemType>"

        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                additionalProperties = itemType.toOpenApiSchema()
            }
        }
    }

    class Unknown : ComputedType() {
        override fun toString() = "unknown"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "object"
            }
        }
    }

    class Generic(val id: String) : ComputedType() {
        override fun toString() = "($id)"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "object"
            }
        }
    }

    class Struct(
        var qualifiedName: String,
        var type: Type,
        var properties: MutableMap<String, ComputedType>,
        var discriminator: Discriminator? = null,
    ) : ComputedType() {
        override fun asRef() = StructRef(qualifiedName)

        override fun toString() = qualifiedName

        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "object"

                val discRef = this@Struct.discriminator
                if (discRef != null) {
                    discriminator = Discriminator().apply {
                        this.propertyName = discRef.property
                        this.mapping = discRef.valueToQualifiedName.mapValues {
                            componentsRef + it.value
                        }
                    }
                }

                properties = this@Struct.properties.map {
                    it.key to it.value.toOpenApiSchema()
                }.toMap()
            }
        }
    }

    class StructRef(val qualifiedName: String) : ComputedType() {
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                `$ref` = componentsRef + qualifiedName
            }
        }
    }

    class Enum(val options: List<String>) : ComputedType() {
        override fun toString() = "enum(${options.joinToString(", ")})"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "string"
                enum = options
            }
        }
    }

    data class Discriminator(
        val property: String,
        val valueToQualifiedName: Map<String, String>
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun traverseType(type: Type, visitedTypes: HashMap<String, ComputedType>): ComputedType {
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

                    val rawComputedType = traverseType(type.rawType, visitedTypes)
                    if (rawComputedType !is ComputedType.Struct) error("Expected raw type to be a struct")
                    visitedTypes.remove(rawComputedType.qualifiedName) // We don't want to leave in the generic type

                    val actualTypeArgs = type.actualTypeArguments.map { traverseType(it, visitedTypes) }
                    val typeParams = rawType.typeParameters

                    rawComputedType.qualifiedName = "$rawComputedType<${actualTypeArgs.joinToString(",")}>"
                    visitedTypes[rawComputedType.qualifiedName] = rawComputedType

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

        is Class<*> -> {
            if (type.isEnum) {
                return ComputedType.Enum(type.enumConstants.map { it.toString() })
            }

            if (type == Unit::class.java || type == Any::class.java) {
                return ComputedType.Unknown()
            }

            val qualifiedName = type.canonicalName
            val existing = visitedTypes[qualifiedName]
            if (existing != null) return existing

            val properties = HashMap<String, ComputedType>()
            val struct = ComputedType.Struct(qualifiedName, type, properties)
            visitedTypes[qualifiedName] = struct

            if (type.isKotlinClass()) {
                val kotlinType = type.kotlin

                val apiDoc = kotlinType.findAnnotation<UCloudApiDoc>()
                if (apiDoc != null) {
                    struct.documentation = apiDoc.documentation
                }

                kotlinType.primaryConstructor?.parameters?.forEach { prop ->
                    if (prop.name == null) return@forEach

                    val classProp = kotlinType.memberProperties.find { it.name == prop.name }
                    val javaFieldAnnotations = (classProp?.javaField?.annotations?.toList() ?: emptyList())
                    val getterAnnotations = classProp?.getter?.annotations ?: emptyList()
                    val annotations: Set<Annotation> = (prop.annotations + javaFieldAnnotations + getterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore }) return@forEach

                    var propType = traverseType(prop.type.javaType, visitedTypes)
                    if (propType is ComputedType.Struct) {
                        // We use a struct ref since classes store them by reference (base class can have doc and ref
                        // can have doc)
                        propType = ComputedType.StructRef(propType.qualifiedName)
                    }

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()
                    if (propApiDoc != null) {
                        propType.documentation = propApiDoc.documentation
                    }

                    var propName = prop.name!!
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
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
                    val annotations: Set<Annotation> = (prop.annotations + javaFieldAnnotations + getterAnnotations).toSet()
                    if (annotations.any { it is JsonIgnore }) return@forEach

                    var propType = traverseType(prop.returnType.javaType, visitedTypes)
                    if (propType is ComputedType.Struct) {
                        // We use a struct ref since classes store them by reference (base class can have doc and ref
                        // can have doc)
                        propType = ComputedType.StructRef((propType as ComputedType.Struct).qualifiedName)
                    }

                    val propApiDoc = annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()
                    if (propApiDoc != null) {
                        propType.documentation = propApiDoc.documentation
                    }

                    var propName = prop.name!!
                    val jsonPropAnnotation = annotations.filterIsInstance<JsonProperty>().firstOrNull()
                    if (jsonPropAnnotation != null) {
                        propName = jsonPropAnnotation.value
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

                return struct
            } else {
                TODO("Non-primitive and non-kotlin class $type")
            }
        }

        else -> {
            error("Unknown thing: $type")
        }
    }
}