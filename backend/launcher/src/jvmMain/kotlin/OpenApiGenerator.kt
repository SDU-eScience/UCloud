package dk.sdu.cloud

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.app.orchestrator.api.JobsProvider
import dk.sdu.cloud.app.orchestrator.api.JobsControl
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.IngoingRequestInterceptor
import dk.sdu.cloud.calls.server.OutgoingCallResponse
import dk.sdu.cloud.micro.PlaceholderServiceDescription
import dk.sdu.cloud.micro.ServiceRegistry
import dk.sdu.cloud.micro.server
import io.ktor.http.*
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
import io.swagger.v3.oas.models.tags.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.*
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.*
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmName
import kotlin.system.exitProcess

private val componentsRef = "#/components/schemas/"

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
    val maturityWarnings = HashSet<String>()
    fun warnMissingMaturity(call: CallDescription<*, *, *>) {
        if (!maturityWarnings.contains(call.namespace)) {
            maturityWarnings.add(call.namespace)
            log.warn("${call.namespace} has no maturity associated with it")
            log.warn("Future warnings for this namespace will be suppressed")
        }
    }

    val groupedCalls = knownCalls.groupBy { call ->
        val maturity = call.apiMaturityOrNull
        if (maturity == null) {
            warnMissingMaturity(call)
        }

        maturity ?: UCloudApiMaturity.Internal(InternalLevel.BETA)
    }

    writeSpecification(
        groupedCalls.filterKeys { it is UCloudApiMaturity.Internal }.values.flatten().toMutableList(),
        File("/tmp/swagger/internal"),
        subtitle = "Internal API"
    )
    writeSpecification(
        groupedCalls.filterKeys { it !is UCloudApiMaturity.Internal }.values.flatten().toMutableList(),
        File("/tmp/swagger/public"),
        subtitle = "Public API"
    )


    log.info("UCloud has ${knownCalls.size} number of calls in total")
    writeSpecification(
        knownCalls,
        File("/tmp/swagger/combined"),
        subtitle = "Full API",
        isFullApi = true,
    )

    run {
        // Provider calls
        val calls = buildList {
            addAll(JobsProvider(PROVIDER_ID_PLACEHOLDER).callContainer)
            addAll(JobsControl.callContainer)
        }

        writeSpecification(
            calls.toMutableList(),
            File("/tmp/swagger/providers-complete"),
            subtitle = "Provider API (UCloud and Provider)"
        )
    }

    run {
        // Provider API only
        val calls = buildList {
            addAll(JobsProvider(PROVIDER_ID_PLACEHOLDER).callContainer)
        }

        writeSpecification(
            calls.toMutableList(),
            File("/tmp/swagger/providers-only"),
            subtitle = "Provider API (Provider only)"
        )
    }
    exitProcess(0)
}

private fun badge(
    label: String,
    message: String,
    color: String,
    altText: String = "$label: $message",
): String {
    return "![$altText](https://img.shields.io/static/v1?label=$label&message=$message&color=$color&style=flat-square)"
}

private fun apiMaturityBadge(level: UCloudApiMaturity): String {
    val label = "API"
    fun normalizeEnum(enum: Enum<*>): String {
        return enum.name.toLowerCase().capitalize()
    }
    return when (level) {
        is UCloudApiMaturity.Internal -> badge(label, "Internal/${normalizeEnum(level.level)}", "red")
        is UCloudApiMaturity.Experimental -> badge(label, "Experimental/${normalizeEnum(level.level)}", "orange")
        UCloudApiMaturity.Stable -> badge(label, "Stable", "green")
        else -> error("unknown level")
    }
}

private fun rolesBadge(roles: Set<Role>): String {
    val message = when (roles) {
        Roles.AUTHENTICATED -> "Authenticated"
        Roles.PRIVILEGED -> "Services"
        Roles.END_USER -> "Users"
        Roles.ADMIN -> "Admin"
        Roles.PUBLIC -> "Public"
        Roles.PROVIDER -> "Provider"
        else -> roles.joinToString(", ")
    }

    return badge("Auth", message, "informational")
}

private fun writeSpecification(
    knownCalls: MutableList<CallDescription<*, *, *>>,
    output: File,
    subtitle: String? = null,
    documentation: String? = null,
    isFullApi: Boolean = false
) {
    val typeRegistry = LinkedHashMap<String, ComputedType>()
    val doc = OpenAPI().apply {
        servers = listOf(Server().url("https://cloud.sdu.dk"))
        info = Info().apply {
            version = "1.0.0"
            title = "UCloud" + if (subtitle == null) "" else " | $subtitle"
            license = License().apply {
                name = "EUPL-1.2"
                url = "https://github.com/SDU-eScience/UCloud/blob/master/LICENSE.md"
            }
            addExtension("x-logo", mapOf("url" to "https://docs.cloud.sdu.dk/dev/_images/logo.png"))
            termsOfService = "https://legal.cloud.sdu.dk"
            contact = Contact().apply {
                email = "support@escience.sdu.dk"
            }
            this.description = documentation
        }

        externalDocs = ExternalDocumentation().apply {
            url = "https://docs.cloud.sdu.dk"
        }

        // NOTE(Dan): Currently made to be rendered by https://github.com/Redocly/redoc

        tags = arrayListOf()
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

                    if (tags.none { it.name == call.containerRef.namespace }) {
                        tags.add(Tag().apply {
                            name = call.containerRef.namespace
                            val displayTitle = call.containerRef.title
                            if (displayTitle != null) addExtension("x-displayName", displayTitle)
                            description = call.containerRef.description
                        })
                    }

                    val doc = call.docOrNull
                    val matchingField = call.field

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

                    val callExtension = CallExtension(call)
                    op.addExtension(CallExtension.EXTENSION, callExtension)

                    val maturity = call.apiMaturityOrNull ?: UCloudApiMaturity.Internal(InternalLevel.BETA)

                    op.description = """
                        ${apiMaturityBadge(maturity)}
                        ${rolesBadge(call.authDescription.roles)}
                        
                        
                    """.trimIndent()
                    if (doc != null) {
                        if (doc.description != null) op.description += doc.description
                        op.summary = doc.summary + " (${call.name})"
                        op.deprecated = matchingField?.hasAnnotation<Deprecated>()
                    }

                    op.operationId = call.fullName
                    if (op.operationId.contains(".")) {
                        // Hack: Delete anything after '.' to allow versioning in call ids
                        op.operationId = op.operationId.substringBefore('.')
                    }
                    op.tags = listOf(call.namespace)
                    op.responses = ApiResponses().apply {
                        set("200", apiResponse(call.successClass.javaType, typeRegistry).apply {
                            if (doc != null) {
                                val json = this.content[ContentType.Application.Json.toString()]
                                if (json != null) {
                                    json.examples = LinkedHashMap()
                                    for (example in doc.examples) {
                                        if (example.statusCode.isSuccess() && example.response != null) {
                                            json.examples[example.name] = createExample(example).apply {
                                                value = example.response
                                            }
                                        }
                                    }
                                    if (json.examples.isEmpty()) json.examples = null
                                }
                            }
                        })

                        doc?.examples?.groupBy { it.statusCode }?.filterKeys { !it.isSuccess() }
                            ?.forEach { (code, examples) ->
                                set(code.value.toString(), apiResponse(call.errorClass.javaType, typeRegistry).apply {
                                    this.description = code.description
                                    val json = this.content[ContentType.Application.Json.toString()]
                                    if (json != null) {
                                        json.examples = LinkedHashMap()
                                        for (example in examples) {
                                            if (example.error != null) {
                                                json.examples[example.name] = createExample(example).apply {
                                                    value = example.error
                                                }
                                            }
                                        }
                                        if (json.examples.isEmpty()) json.examples = null
                                    }
                                })
                            }

                        doc?.errors?.forEach { err ->
                            val description = computeIfAbsent(
                                err.statusCode.value.toString(),
                                { apiResponse(call.errorClass.javaType, typeRegistry) }
                            )

                            description.description = err.description
                        }

                        default = apiResponse(call.errorClass.javaType, typeRegistry)
                    }

                    if (call.successClass.javaType != Unit::class) {
                        callExtension.responseType = traverseType(call.successClass.javaType, typeRegistry)
                    }

                    val body = http.body
                    if (body != null && !body.ref.descriptor.serialName.startsWith("kotlin.Unit")) {
                        val requestType = traverseType(call.requestClass.javaType, typeRegistry)
                        callExtension.requestType = requestType
                        op.requestBody = RequestBody()
                        op.requestBody.content = Content().apply {
                            set(ContentType.Application.Json.toString(), MediaType().apply {
                                if (doc != null && body is HttpBody.BoundToEntireRequest<*>) {
                                    examples = LinkedHashMap()
                                    doc.examples.forEach { example ->
                                        if (example.request != null) {
                                            examples[example.name] = createExample(example).apply {
                                                value = example.request
                                            }
                                        }
                                    }
                                    if (examples.isEmpty()) examples = null
                                }
                                schema = requestType.toOpenApiSchema()
                            })
                        }
                    }

                    val params = http.params
                    if (params != null) {
                        val requestType = traverseType(call.requestClass.javaType, typeRegistry) as? ComputedType.Struct
                            ?: error("Query params bound to a non-class ${call.fullName}")
                        callExtension.requestType = requestType

                        op.parameters = params.parameters.map { p ->
                            when (p) {
                                is HttpQueryParameter.Property<*> -> {
                                    Parameter().apply {
                                        name = p.property
                                        schema = requestType.properties[name]!!.toOpenApiSchema()
                                        `in` = "query"
                                    }
                                }

                                else -> error("unknown property")
                            }
                        }
                    }
                }
            }

        for ((k, v) in typeRegistry) {
            val struct = v as? ComputedType.Struct
            if (struct != null) {
                val disc = struct.discriminator
                if (disc != null) {
                    for ((prop, typeName) in disc.valueToQualifiedName) {
                        val child = typeRegistry[typeName] as? ComputedType.Struct
                        if (child != null) {
                            child.properties[disc.property] = ComputedType.Enum(listOf(prop))
                            child.parent = v.asRef()
                        }
                    }

                    struct.properties[disc.property] = ComputedType.Enum(disc.valueToQualifiedName.keys.toList())
                }
            }
        }

        components.schemas = typeRegistry.map { (k, v) ->
            (k) to v.toOpenApiSchema()
        }.toMap()
    }

    println("UCloud has ${typeRegistry.size} number of types")
    output.mkdirs()
    if (isFullApi) generateTypeScriptCode(output, doc, typeRegistry)
    generateSpringMvcCode()
    if (isFullApi) injectMarkdownDocs(doc, typeRegistry)
    File(output, "swagger.yaml").writeText(Yaml.pretty(doc))
    File(output, "swagger.json").writeText(Json.pretty(doc))
}

private fun createExample(example: UCloudCallDoc.Example<out Any, out Any, out Any>): Example {
    return Example().apply {
        summary = example.summary
        description = example.description
    }
}

private fun apiResponse(type: Type, registry: LinkedHashMap<String, ComputedType>): ApiResponse {
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
        override fun toString() = "dict_${itemType}_"

        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                additionalProperties = itemType.asRef().toOpenApiSchema()
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
        override fun toString() = "_${id}_"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "object"
            }
        }
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

        data class GenericInfo(
            var baseType: String,
            var typeParameters: List<ComputedType>,
        )
    }

    class StructRef(val qualifiedName: String) : ComputedType() {
        override fun toOpenApiSchema(): Schema<*> {
            return ComposedSchema().apply {
                `$ref` = componentsRef + qualifiedName + if (this@StructRef.nullable == true) "_Opt" else ""
                this.description = this@StructRef.documentation
                this.deprecated = this@StructRef.deprecated
                this.nullable = this@StructRef.nullable
            }
        }
    }

    class Enum(val options: List<String>) : ComputedType() {
        override fun toString() = "enum_${options.joinToString("_")})"
        override fun toOpenApiSchema(): Schema<*> {
            return baseSchema().apply {
                type = "string"
                enum = options
            }
        }
    }

    class CustomSchema(private val schema: Schema<*>, val tsDefinition: ComputedType? = null) : ComputedType() {
        override fun toOpenApiSchema(): Schema<*> = schema
    }

    data class Discriminator(
        val property: String,
        val valueToQualifiedName: Map<String, String>,
    )
}

@OptIn(ExperimentalStdlibApi::class)
private fun traverseType(type: Type, visitedTypes: LinkedHashMap<String, ComputedType>): ComputedType {
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

                    if (rawType == BulkRequest::class.java) {
                        return ComputedType.CustomSchema(
                            ComposedSchema().apply {
                                oneOf = listOf(
                                    actualTypeArgs[0].asRef().toOpenApiSchema().apply {
                                        this.name = "Single (${actualTypeArgs[0]})"
                                    },
                                    Schema<Any?>().apply {
                                        this.type = "object"
                                        this.name = "Bulk (${actualTypeArgs[0]})"

                                        properties = mapOf(
                                            "type" to Schema<Any?>().apply {
                                                this.type = "string"
                                                enum = listOf("bulk")
                                            },
                                            "items" to ArraySchema().apply {
                                                items = actualTypeArgs[0].asRef().toOpenApiSchema()
                                            }
                                        )
                                    }
                                )
                            },
                            tsDefinition = rawComputedType.apply {
                                qualifiedName = "${rawComputedType}_" +
                                    "${actualTypeArgs.joinToString(",") { it.toString().replace(".", "_") }}_ "

                                genericInfo = ComputedType.Struct.GenericInfo(
                                    initialType.qualifiedName,
                                    actualTypeArgs
                                )

                                visitedTypes[qualifiedName] = this
                            }
                        )
                    }

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
