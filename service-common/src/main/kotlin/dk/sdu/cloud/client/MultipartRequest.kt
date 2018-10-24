package dk.sdu.cloud.client

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.service.InputParsingResponse
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.defaultForFile
import io.ktor.pipeline.PipelineContext
import io.ktor.request.receiveOrNull
import io.ktor.util.asStream
import kotlinx.coroutines.experimental.io.jvm.javaio.copyTo
import java.io.File
import java.io.InputStream
import java.lang.reflect.ParameterizedType
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

class StreamingFile(
    val contentType: ContentType,
    val length: Long,
    val fileName: String,
    val payload: InputStream
) {
    companion object {
        fun fromFile(file: File): StreamingFile = StreamingFile(
            ContentType.defaultForFile(file),
            file.length(),
            file.name,
            file.inputStream()
        )
    }
}

private data class IngoingMultipart(
    val description: RESTCallDescription<*, *, *, *>,
    val context: PipelineContext<*, ApplicationCall>,
    val multipart: MultiPartData
)

class MultipartRequest<Request : Any> private constructor() {
    private var outgoing: Request? = null
    private var ingoing: IngoingMultipart? = null

    suspend fun receiveBlocks(consumer: (Request) -> Unit) {
        val ingoing = ingoing ?: throw IllegalStateException("Not an ingoing MultipartRequest")

        @Suppress("UNCHECKED_CAST")
        val requestType = getRequestTypeFromDescription(ingoing.description) as KClass<Request>
        val constructor = findConstructor(requestType)

        val knownProps = requestType.memberProperties.associateBy { it.name }
        val partsSeen = HashSet<String>()

        val builder = HashMap<String, Any?>()
        var isSendingFinal = false

        fun send() {
            val params = constructor.parameters.mapNotNull {
                val value = builder[it.name]
                if (value == null) {
                    when {
                        it.type.isMarkedNullable -> it to null
                        it.isOptional -> null
                        else -> throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                    }
                } else {
                    it to value
                }
            }.toMap()

            @Suppress("UNCHECKED_CAST")
            val block = constructor.callBy(params) as Request
            consumer(block)

            isSendingFinal = partsSeen.size == knownProps.size
        }

        ingoing.multipart.forEachPart { part ->
            val name = part.name ?: run { part.dispose(); return@forEachPart }

            val prop = knownProps[name] ?: run { part.dispose(); return@forEachPart }

            partsSeen.add(prop.name)
            val parsedPart = parsePart(ingoing, prop, part)
            builder[name] = parsedPart

            if (parsedPart is StreamingFile) {
                send()
                builder.remove(name) // We need to remove StreamingFiles from the builder.
            }

            part.dispose()
        }

        if (partsSeen.size != knownProps.size) {
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        if (!isSendingFinal) send()
    }

    private fun findConstructor(type: KClass<*>): KFunction<Any> {
        return type.primaryConstructor ?: type.constructors.single()
    }

    private fun parsePart(ingoing: IngoingMultipart, prop: KProperty1<Request, *>, part: PartData): Any? {
        val propType = prop.returnType.classifier as? KClass<*>
            ?: throw IllegalStateException("Cannot find type of ${prop.name}")

        val contentType = part.headers[HttpHeaders.ContentType]
            ?.let {
                try {
                    ContentType.parse(it)
                } catch (ex: BadContentTypeFormatException) {
                    null
                }
            } ?: ContentType.Any


        when (part) {
            is PartData.FormItem -> {

                val isJson = contentType.match(ContentType.Application.Json)

                return if (isJson) {
                    try {
                        defaultMapper.readValue(part.value, propType.java)
                    } catch (ex: Exception) {
                        when (ex) {
                            is JsonMappingException, is JsonParseException -> {
                                log.debug(ex.stackTraceToString())
                                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                            }

                            else -> {
                                log.warn(ex.stackTraceToString())
                                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                            }
                        }
                    }
                } else {
                    when (propType) {
                        String::class -> part.value
                        Byte::class -> part.value.toByteOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Short::class -> part.value.toShortOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Int::class -> part.value.toIntOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Long::class -> part.value.toLongOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Short::class -> part.value.toShortOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Double::class -> part.value.toDoubleOrNull()
                            ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                        Boolean::class -> part.value.toBoolean()
                        else -> throw IllegalArgumentException("Cannot convert value ${prop.name}")
                    }
                }
            }

            is PartData.FileItem -> {
                return StreamingFile(
                    contentType,
                    part.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L,
                    part.originalFileName ?: "unknown" ,
                    part.provider().asStream()
                )
            }

            else -> TODO()
        }
    }

    companion object : RequestBodyParamMarshall<MultipartRequest<*>>, Loggable {
        override val log = logger()

        private fun getRequestTypeFromDescription(restCallDescription: RESTCallDescription<*, *, *, *>): KClass<Any> {
            val requestType = (restCallDescription.requestType.type as ParameterizedType).actualTypeArguments[0]

            @Suppress("UNCHECKED_CAST")
            return (requestType as? Class<Any>)
                ?.takeIf { it.isKotlinClass() }
                ?.kotlin ?: throw IllegalStateException("The generic of MultipartRequest must be a Kotlin class")
        }

        fun <R : Any> create(request: R): MultipartRequest<R> {
            val result = MultipartRequest<R>()
            result.outgoing = request
            return result
        }

        override suspend fun deserializeBody(
            description: RESTCallDescription<*, *, *, *>,
            context: PipelineContext<*, ApplicationCall>
        ): InputParsingResponse {
            with(context) {
                val multipart: MultiPartData = call.receiveOrNull() ?: return InputParsingResponse.MissingAndRequired
                val result = MultipartRequest<Any>().apply {
                    ingoing = IngoingMultipart(description, context, multipart)
                }

                // We could eagerly parse as much as possible here.

                return InputParsingResponse.Parsed(result)
            }
        }

        override fun serializeBody(
            description: RESTCallDescription<*, *, *, *>,
            body: RESTBody<*, MultipartRequest<*>>,
            value: MultipartRequest<*>
        ): Any {
            if (body !is RESTBody.BoundToEntireRequest<*>) {
                throw IllegalStateException("Can only bind to the entire body!")
            }

            val klass = getRequestTypeFromDescription(description)

            val outgoing = value.outgoing ?: throw IllegalStateException()

            return MultiPartContent.build {
                // TODO The order of this is very important. It must match the same order that the constructor is using
                klass.memberProperties.forEach { prop ->
                    val name = prop.name
                    val propValue = prop.get(outgoing)

                    when (propValue) {
                        // "Primitive" types
                        is String, is Byte, is Short, is Int, is Long, is Float, is Double, is Boolean -> {
                            add(name, propValue.toString())
                        }

                        is StreamingFile -> {
                            add(
                                name = name,
                                filename = propValue.fileName,

                                contentType = propValue.contentType,

                                headers = Headers.build {
                                    append(HttpHeaders.ContentLength, propValue.length.toString())
                                },

                                writer = { propValue.payload.use { it.copyTo(this) } }
                            )
                        }

                        else -> {
                            add(
                                name = name,
                                // TODO Will lose generic information
                                text = defaultMapper.writeValueAsString(propValue),
                                contentType = ContentType.Application.Json
                            )
                        }
                    }
                }
            }
        }
    }
}
