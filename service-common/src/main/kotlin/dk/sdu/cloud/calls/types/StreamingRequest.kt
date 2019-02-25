package dk.sdu.cloud.calls.types

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpBody
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.HttpClientConverter
import dk.sdu.cloud.calls.http
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.HttpServerConverter
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.stackTraceToString
import io.ktor.application.ApplicationCall
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.defaultForFile
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.cio.readChannel
import io.ktor.util.pipeline.PipelineContext
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.copyTo
import kotlinx.coroutines.io.jvm.javaio.toByteReadChannel
import kotlinx.coroutines.io.jvm.javaio.toInputStream
import kotlinx.coroutines.io.readRemaining
import kotlinx.io.core.ExperimentalIoApi
import kotlinx.io.core.IoBuffer
import java.io.File
import java.io.InputStream
import java.lang.reflect.ParameterizedType
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
    val payload: InputStream
        get() = channel.toInputStream()

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

data class IngoingMultipart(
    val description: CallDescription<*, *, *>,
    val context: PipelineContext<*, ApplicationCall>,
    val multipart: StreamingMultipart
)

/**
 * Provides a way for services to communicate in multipart requests.
 *
 * The underlying [Request] type is a bit special, so you must take care both when sending and receiving messages.
 * The [StreamingRequest] will attempt to deliver a block of the request as soon as we would have to block for a large
 * chunk. Concretely, [StreamingRequest] considers all instances of [StreamingFile] to be a large chunk and will
 * deliver a message to the server as soon as the first byte is received. The [StreamingFile] then provides access to
 * this chunk through the [StreamingFile.payload].
 *
 * Because of the need to deliver the [Request] when the first byte of a [StreamingFile] is seen we must put some
 * constraints on the [Request]. Specifically anything coming after the initial [StreamingFile], including the
 * initial [StreamingFile], must be nullable. This allows the [StreamingRequest] to deliver the [Request] with
 * partial data.
 *
 * A [StreamingFile] will only ever be not-null in a single call of [StreamingRequest.Ingoing.receiveBlocks].
 * The [StreamingFile.payload] is not valid outside of [StreamingRequest.Ingoing.receiveBlocks].
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
 *     val multipart = callDescription<StreamingRequest<FormRequest>, Unit, Unit> {
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
sealed class StreamingRequest<Request : Any> {
    class Ingoing<Request : Any>(
        private val ingoing: IngoingMultipart
    ) : StreamingRequest<Request>() {
        suspend fun receiveBlocks(consumer: suspend (Request) -> Unit) {
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
            val isJson = contentType.match(ContentType.Application.Json) && propType != StreamingFile::class

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
    }

    class Outgoing<Request : Any>(
        private val request: Request
    ) : StreamingRequest<Request>(), HttpClientConverter.OutgoingBody {
        override fun clientOutgoingBody(call: CallDescription<*, *, *>): OutgoingContent {
            if (call.http.body !is HttpBody.BoundToEntireRequest<*>) {
                throw IllegalStateException("Can only bind to the entire body!")
            }

            val klass = getRequestTypeFromDescription(call)
            val constructor = findConstructor(klass)

            return OutgoingMultiPartContent.build {
                // The order of this is very important. It must match the same order that the constructor is using
                val memberProperties = klass.memberProperties

                constructor.parameters.forEach { param ->
                    val prop = memberProperties.find { it.name == param.name }
                        ?: throw IllegalStateException("Request types must be simple data classes!")

                    val name = prop.name
                    val propValue = prop.get(request)

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

                                writer = {
                                    propValue.channel.copyTo(this, Long.MAX_VALUE)
                                }
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


        private suspend fun ByteReadChannel.copyToImpl(dst: ByteWriteChannel, limit: Long): Long {
            val buffer = IoBuffer.Pool.borrow()
            val dstNeedsFlush = !dst.autoFlush

            try {
                var copied = 0L

                while (true) {
                    val remaining = limit - copied
                    if (remaining == 0L) break
                    buffer.resetForWrite(minOf(buffer.capacity.toLong(), remaining).toInt())

                    val size = readAvailable(buffer)
                    if (size == -1) break

                    dst.writeFully(buffer)
                    copied += size

                    if (dstNeedsFlush && availableForRead == 0) {
                        dst.flush()
                    }
                }
                return copied
            } catch (t: Throwable) {
                dst.close(t)
                throw t
            } finally {
                buffer.release(IoBuffer.Pool)
            }
        }
    }


    fun asIngoing(): StreamingRequest.Ingoing<Request> = this as StreamingRequest.Ingoing<Request>

    companion object : HttpServerConverter.IngoingBody<StreamingRequest<*>>, Loggable {
        override val log = logger()

        override suspend fun serverIngoingBody(
            description: CallDescription<*, *, *>,
            call: HttpCall
        ): StreamingRequest<*> {
            val multipart = try {
                StreamingMultipart.construct(call)
            } catch (ex: Exception) {
                log.debug(ex.stackTraceToString())
                throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }

            // We could eagerly parse as much as possible here.
            return StreamingRequest.Ingoing<Any>(IngoingMultipart(description, call, multipart))
        }

        private fun getRequestTypeFromDescription(callDescription: CallDescription<*, *, *>): KClass<Any> {
            val requestType = (callDescription.requestType.type as ParameterizedType).actualTypeArguments[0]

            @Suppress("UNCHECKED_CAST")
            return (requestType as? Class<Any>)
                ?.takeIf { it.isKotlinClass() }
                ?.kotlin ?: throw IllegalStateException("The generic of StreamingRequest must be a Kotlin class")
        }

        private fun findConstructor(type: KClass<*>): KFunction<Any> {
            return type.primaryConstructor ?: type.constructors.single()
        }
    }
}
