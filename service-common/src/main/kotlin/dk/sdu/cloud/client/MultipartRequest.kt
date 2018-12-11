package dk.sdu.cloud.client

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.service.InputParsingResponse
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.StreamingMultipart
import dk.sdu.cloud.service.StreamingPart
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.defaultForFile
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.cio.readChannel
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.copyTo
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.coroutines.io.readRemaining
import kotlinx.io.core.ExperimentalIoApi
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
    val length: Long?,
    val fileName: String?,
    val channel: ByteReadChannel
) {
    @Deprecated("use channel instead")
    @UseExperimental(ExperimentalIoApi::class)
    constructor(
        contentType: ContentType,
        length: Long?,
        fileName: String?,
        payload: InputStream
    ) : this(contentType, length, fileName, payload.toByteReadChannel())

    @Deprecated("use channel instead", ReplaceWith("channel"))
    val payload: InputStream get() = channel.toInputStream()

    companion object {
        @UseExperimental(KtorExperimentalAPI::class)
        fun fromFile(file: File): StreamingFile = StreamingFile(
            ContentType.defaultForFile(file),
            file.length(),
            file.name,
            file.readChannel()
        )
    }
}

private data class IngoingMultipart(
    val description: RESTCallDescription<*, *, *, *>,
    val context: PipelineContext<*, ApplicationCall>,
    val multipart: StreamingMultipart
)

/**
 * Provides a way for services to communicate in multipart requests.
 *
 * The underlying [Request] type is a bit special, so you must take care both when sending and receiving messages.
 * The [MultipartRequest] will attempt to deliver a block of the request as soon as we would have to block for a large
 * chunk. Concretely, [MultipartRequest] considers all instances of [StreamingFile] to be a large chunk and will
 * deliver a message to the server as soon as the first byte is received. The [StreamingFile] then provides access to
 * this chunk through the [StreamingFile.payload].
 *
 * Because of the need to deliver the [Request] when the first byte of a [StreamingFile] is seen we must put some
 * constraints on the [Request]. Specifically anything coming after the initial [StreamingFile], including the
 * initial [StreamingFile], must be nullable. This allows the [MultipartRequest] to deliver the [Request] with
 * partial data.
 *
 * A [StreamingFile] will only ever be not-null in a single call of [receiveBlocks]. The [StreamingFile.payload] is not
 * valid outside of [receiveBlocks].
 *
 * Consider the following example:
 *
 * ```kotlin
 * data class Wrapper<T>(val value: T)
 *
 * data class JsonPayload(
 *     val foo: Int,
 *     val bar: String,
 *     val list: List<Wrapper<String>>
 * )
 *
 * data class FormRequest(
 *     val normal: String,
 *     val json: JsonPayload,
 *     val streamingOne: StreamingFile?, // everything past the initial StreamingFile is nullable
 *     val normal2: String?,
 *     val streamingTwo: StreamingFile?
 * )
 *
 * object MultipartDescriptions : RESTDescriptions("foo") {
 *     val multipart = callDescription<MultipartRequest<FormRequest>, Unit, Unit> {
 *         name = "multipart"
 *         method = HttpMethod.Post
 *
 *         auth {
 *             access = AccessRight.READ
 *         }
 *
 *         path {
 *             +"foo"
 *         }
 *
 *         body { bindEntireRequestFromBody() }
 *      }
 * }
 *
 * implement(MultipartDescriptions.multipart) { req ->
 *     // On a valid call this will call the handler of receiveBlocks twice.
 *     // On invalid calls it might be called less than twice (either 0 or 1).
 *     req.receiveBlocks {
 *         println(it)
 *
 *         if (it.streamingOne != null) {
 *             println("Streaming one: ${it.streamingOne.payload.bufferedReader().readText()}")
 *         }
 *
 *         if (it.streamingTwo != null) {
 *             println("Streaming two: ${it.streamingTwo.payload.bufferedReader().readText()}")
 *         }
 *     }
 *
 *     ok(Unit)
 * }
 * ```
 */
class MultipartRequest<Request : Any> private constructor() {
    private var outgoing: Request? = null
    private var ingoing: IngoingMultipart? = null

    suspend fun receiveBlocks(consumer: suspend (Request) -> Unit) {
        val ingoing = ingoing ?: throw IllegalStateException("Not an ingoing MultipartRequest")

        @Suppress("UNCHECKED_CAST")
        val requestType = getRequestTypeFromDescription(ingoing.description) as KClass<Request>
        val constructor = findConstructor(requestType)

        val requiredProperties = constructor.parameters
            .asSequence()
            .filter { !it.isOptional }
            .mapNotNull { it.name }
            .toSet()

        val knownProps = requestType.memberProperties.associateBy { it.name }
        val partsSeen = HashSet<String>()

        val builder = HashMap<String, Any?>()
        var hasUnsentData = false

        suspend fun send() {
            hasUnsentData = false
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
            val block = try {
                constructor.callBy(params) as Request
            } catch (ex: Exception) {
                log.debug(ex.stackTraceToString())

                when (ex) {
                    is RPCException -> throw ex
                    else -> throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
            }

            consumer(block)
        }

        while (true) {
            val nextPart = ingoing.multipart.readPart() ?: break
            try {
                val name = nextPart.partHeaders.contentDisposition.name ?: continue
                val prop = knownProps[name] ?: continue
                hasUnsentData = true

                partsSeen.add(prop.name)
                val parsedPart = parsePart(prop, nextPart)
                builder[name] = parsedPart

                if (parsedPart is StreamingFile) {
                    send()
                    builder.remove(name) // We need to remove StreamingFiles from the builder.
                }

            } finally {
                nextPart.dispose()
            }
        }

        if (!partsSeen.containsAll(requiredProperties)) {
            log.debug("We expected to see $requiredProperties but we only saw $partsSeen")
            throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
        }

        if (hasUnsentData) send()
    }

    private suspend fun parsePart(prop: KProperty1<Request, *>, part: StreamingPart): Any? {
        val propType = prop.returnType.classifier as? KClass<*>
            ?: throw IllegalStateException("Cannot find type of ${prop.name}")

        val contentType = part.partHeaders.contentType ?: ContentType.Application.OctetStream
        val isJson = contentType.match(ContentType.Application.Json)

        suspend fun StreamingPart.value(): String {
            val packet = channel.readRemaining(1024 * 64)
            try {
                if (!channel.isClosedForRead) {
                    throw RPCException.fromStatusCode(HttpStatusCode.PayloadTooLarge, "Entity too large")
                }

                return packet.readText()
            } finally {
                packet.release()
            }
        }

        return if (isJson) {
            try {
                defaultMapper.readValue(part.value(), propType.java)
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
            when {
                propType == String::class -> part.value()
                propType == Byte::class -> part.value().toByteOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Short::class -> part.value().toShortOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Int::class -> part.value().toIntOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Long::class -> part.value().toLongOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Short::class -> part.value().toShortOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Double::class -> part.value().toDoubleOrNull()
                    ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                propType == Boolean::class -> part.value().toBoolean()
                propType.java.isEnum -> {
                    val value = part.value()
                    propType.java.enumConstants.find { (it as Enum<*>).name == value }
                        ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
                propType == StreamingFile::class -> {
                    StreamingFile(
                        contentType,
                        part.partHeaders.contentLength,
                        part.partHeaders.contentDisposition.parameter(ContentDisposition.Parameters.FileName),
                        part.channel
                    )
                }
                else -> {
                    log.info("Could not convert item $prop from value ${part.value()}")
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
            }
        }
    }

    companion object : RequestBodyParamMarshall<MultipartRequest<*>>, Loggable {
        override val log = logger()

        private fun findConstructor(type: KClass<*>): KFunction<Any> {
            return type.primaryConstructor ?: type.constructors.single()
        }

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
            val multipart = try {
                // Should this ever break we should go back to using call.receiveOrNull() with a MultiPartData type
                StreamingMultipart.construct(context)
            } catch (ex: Exception) {
                log.debug(ex.stackTraceToString())
                return InputParsingResponse.MissingAndRequired
            }

            val result = MultipartRequest<Any>().apply {
                ingoing = IngoingMultipart(description, context, multipart)
            }

            // We could eagerly parse as much as possible here.
            return InputParsingResponse.Parsed(result)
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
            val constructor = findConstructor(klass)

            val outgoing = value.outgoing ?: throw IllegalStateException()

            return MultiPartContent.build {
                // The order of this is very important. It must match the same order that the constructor is using
                val memberProperties = klass.memberProperties

                constructor.parameters.forEach { param ->
                    val prop = memberProperties.find { it.name == param.name }
                        ?: throw IllegalStateException("Request types must be simple data classes!")

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
