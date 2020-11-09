package dk.sdu.cloud

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
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.javaType
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

        // TODO(Dan): Namespacing is not perfect but changing it will require writing our own schema generator
        // TODO(Dan): Nullability information is lost but changing it will require writing our own schema generator
        // TODO(Dan): Field documentation from @UCloudApiDoc does not work currently
        // TODO(Dan): Discriminator is not correctly parsed by swagger-core. It correctly find the discriminator
        //  property but does not detect the values

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

        val converters = ModelConverters()
        converters.addPackageToSkip("kotlin")

        paths = Paths()
        components = Components()
        components.schemas = HashMap()

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
                        set("200", apiResponse(call.namespace, call.successType.type).apply {
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
                                set(code.value.toString(), apiResponse(call.namespace, call.errorType.type).apply {
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
                                { apiResponse(call.namespace, call.errorType.type) }
                            )

                            description.description = err.description
                        }

                        default = apiResponse(call.namespace, call.errorType.type)
                    }

                    val body = http.body
                    if (body != null && body.ref.type != Unit::class.java) {
                        addTypeTree(converters, call.namespace, body.ref.type)
                        op.requestBody = RequestBody()
                        op.requestBody.content = Content().apply {
                            set(ContentType.Application.Json.toString(), MediaType().apply {
                                if (doc != null && body is HttpBody.BoundToEntireRequest<*>) {
                                    examples = HashMap()
                                    doc.examples.forEach { example ->
                                        examples[example.name] = createExample(example)
                                    }
                                }
                                schema = schemaFromType(body.ref.type, call.namespace)
                            })
                        }
                    }

                    val params = http.params
                    if (params != null) {
                        op.parameters = params.parameters.map { p ->
                            when (p) {
                                is HttpQueryParameter.Property<*, *> -> {
                                    val type = p.property.returnType.javaType
                                    addTypeTree(converters, call.namespace, type)

                                    Parameter().apply {
                                        name = p.property.name
                                        description = p.property.returnType.javaClass.apiDoc()
                                        `in` = "query"

                                        val jvmClass = type.jvmClass
                                        schema = schemaFromType(jvmClass, call.namespace)

                                    }
                                }
                            }
                        }
                    }

                    // NOTE(Dan): Request type is added above
                    addTypeTree(converters, call.namespace, call.successType.type)
                    addTypeTree(converters, call.namespace, call.errorType.type)
                }
            }
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

private fun schemaFromType(type: Type, namespace: String): Schema<*> {
    var baseType: Class<*>? = null
    var reference = ""
    when (type) {
        java.lang.Short::class.java, Short::class.java -> {
            return Schema<Any?>().apply {
                this.type = "integer"
                this.format = "int16"
            }
        }

        Integer::class.java, Int::class.java -> {
            return Schema<Any?>().apply {
                this.type = "integer"
                this.format = "int32"
            }
        }

        java.lang.Long::class.java, Long::class.java -> {
            return Schema<Any?>().apply {
                this.type = "integer"
                this.format = "int64"
            }
        }

        java.lang.Float::class.java, Float::class.java -> {
            return Schema<Any?>().apply {
                this.type = "number"
                this.format = "float32"
            }
        }

        java.lang.Double::class.java, Double::class.java -> {
            return Schema<Any?>().apply {
                this.type = "number"
                this.format = "float64"
            }
        }

        String::class.java -> {
            return Schema<Any?>().apply {
                this.type = "string"
            }
        }

        java.lang.Boolean::class.java, Boolean::class.java -> {
            return Schema<Any?>().apply {
                this.type = "boolean"
            }
        }

        is Class<*> -> {
            baseType = type
            reference = type.simpleName
        }

        is GenericArrayType -> {

        }

        is ParameterizedType -> {
            if (type.rawType.jvmClass == List::class.java) {
                return ArraySchema().apply {
                    items = schemaFromType(
                        (type.actualTypeArguments.first() as? WildcardType)
                            ?.upperBounds
                            ?.firstOrNull() ?: error("Unsupported array type"),
                        namespace
                    )
                }
            } else {
                reference = type.rawType.jvmClass.simpleName +
                    type.actualTypeArguments.joinToString("") {
                        (it as? WildcardType)?.upperBounds?.firstOrNull()?.typeName?.substringAfterLast('.')
                            ?: it.typeName.substringAfterLast('.')
                    }
            }
        }

        is TypeVariable<*> -> {

        }

        is WildcardType -> {

        }
    }

    return Schema<Any?>().apply {
        if (type == Unit::class.java) {
            this.example(Unit)
            this.type = "object"
        } else if (type is Class<*> && type.isEnum) {
            this.type = "string"
            enum = type.enumConstants.map { it.toString() }
        } else {
            `$ref` = "$componentsRef${namespace}.${reference}"
        }
    }
}

private fun OpenAPI.addTypeTree(
    converters: ModelConverters,
    namespace: String,
    type: Type
) {
    // NOTE(Dan):
    //   Doing this takes care of most of the work, unfortunately, we really want this to do some
    //   namespacing. We do some over simplification and simply put all of the types in the same
    //   namespace. To do this we must seek out all refs and update them with the namespaced version.
    val requestTree = converters.readAll(type)
    for ((name, schema) in requestTree) {
        components.schemas["${namespace}.$name"] = schema.namespace(namespace)
    }
}

private fun <T> Schema<T>.namespace(namespace: String): Schema<T> {
    `$ref` = `$ref`.putRefInNamespace(namespace)
    if (properties != null) {
        for ((_, prop) in properties) {
            prop.namespace(namespace)
        }
    }

    if (this is ArraySchema) {
        if (items != null) {
            items.namespace(namespace)
        }
    }

    if (this is ComposedSchema) {
        allOf?.forEach { it.namespace(namespace) }
        anyOf?.forEach { it.namespace(namespace) }
        oneOf?.forEach { it.namespace(namespace) }
    }

    val additionalProperties = additionalProperties
    if (additionalProperties is Schema<*>) {
        additionalProperties.namespace(namespace)
    }

    return this
}

private fun String?.putRefInNamespace(namespace: String): String? {
    if (this == null) return null
    return componentsRef + namespace + "." + removePrefix(componentsRef)
}

private fun Class<*>.apiDoc(): String {
    return annotations.filterIsInstance<UCloudApiDoc>().firstOrNull()?.documentation ?: "No documentation provided"
}

private fun apiResponse(ns: String, type: Type): ApiResponse {
    return ApiResponse().apply {
        description = (type as? Class<*>)?.apiDoc() ?: "No documentation provided"

        if (type == Unit::class.java) {
            description = "No response"
        }

        content = Content().apply {
            set("application/json", MediaType().apply {
                schema = schemaFromType(type, ns)
            })
        }
    }
}