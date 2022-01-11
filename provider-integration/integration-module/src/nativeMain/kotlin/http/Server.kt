package dk.sdu.cloud.http

import dk.sdu.cloud.ProcessingScope
import dk.sdu.cloud.base64Encode
import dk.sdu.cloud.plugins.storage.posix.S_ISREG
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.utils.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.*
import kotlinx.coroutines.launch
import platform.posix.*

typealias SocketChannel = NativeFile

fun SocketChannel.write(buffer: ByteBuffer) {
    var bytesToWrite = buffer.readerRemaining()
    while (bytesToWrite > 0) {
        val written = write(fd, buffer.rawMemoryPinned.addressOf(buffer.readerIndex), bytesToWrite.toULong())
        if (written <= 0) throw EndOfStreamException()
        buffer.readerIndex += written.toInt()
        bytesToWrite = buffer.readerRemaining()
    }
}

class HttpClientSession<AppData>(
    private val channel: SocketChannel,
    private val outs: ByteBuffer,
    val appData: AppData,
) {
    // An output buffer which can be used by application logic. The scratch should only be used from inside a
    // request handler. Long-running responses should use a separate buffer.
    val outputScratch = allocateDirect(1024 * 8)

    var closing: Boolean = false

    fun sendWebsocketFrame(
        opcode: WebSocketOpCode,
        payload: ByteBuffer,
        mask: Boolean = false
    ) {
        val size = payload.readerRemaining()
        with(outs) {
            clear()
            val maskingKey: Int = secureRandomInt()
            put(((0b1000 shl 4) or opcode.opcode).toByte())

            val maskBit = if (mask) 0b1 else 0b0
            val initialPayloadByte = when {
                size < 126 -> size
                size < 65536 -> 126
                else -> 127
            }
            put(((maskBit shl 7) or initialPayloadByte).toByte())
            if (initialPayloadByte == 126) {
                putShort(size.toShort())
            } else if (initialPayloadByte == 127) {
                putLong(size.toLong())
            }

            if (mask) {
                // TODO This is probably nowhere near correct. Haven't tested, not fully awake.
                for (i in 0 until (size / 4)) {
                    val idx = (i * 4) + payload.readerIndex
                    payload.putInt(
                        idx,
                        payload.getInt(idx) xor maskingKey
                    )
                }

                val rem = size % 4
                if (rem != 0) {
                    val idx = (size / 4) + 1
                    val truncatedMask = maskingKey xor ((1 shl (rem * 8)) - 1)
                    payload.putInt(
                        idx,
                        payload.getInt(idx) xor truncatedMask
                    )
                }
            }

            channel.write(this)

            // TODO We probably need to do something about indexes here
            channel.write(payload)
        }
    }

    fun sendHttpResponseWithFile(path: String, additionalHeaders: List<Header> = emptyList()) {
        memScoped {
            val openFile = NativeFile.open(path, readOnly = true)
            try {
                val st = alloc<stat>()
                if (fstat(openFile.fd, st.ptr) < 0) error("Could not stat file: $path")
                if (st.st_mode and S_ISREG == 0U) error("Open file is not a regular file: $path")

                sendHttpResponse(
                    200,
                    defaultHeaders(payloadSize = st.st_size) + additionalHeaders +
                        listOf(
                            Header(
                                "Content-Type",
                                mimeTypes["." + path.substringAfterLast('.')] ?: "text/plain"
                            )
                        )
                )

                var readBytes = 0
                while (readBytes < st.st_size) {
                    try {
                        outs.clear()
                        openFile.readAtLeast(1, outs)
                        readBytes += outs.readerRemaining()

                        channel.write(outs)
                    } catch (ex: EndOfStreamException) {
                        // I guess we are done a bit early
                        break
                    }
                }
            } finally {
               openFile.close()
            }
        }
    }

    fun sendHttpResponseWithData(statusCode: Int, additionalHeaders: List<Header>, data: ByteArray) {
        sendHttpResponse(statusCode, defaultHeaders(payloadSize = data.size.toLong()) + additionalHeaders)
        channel.writeData(data)
    }

    fun sendHttpResponse(statusCode: Int, headers: List<Header>) {
        check(statusCode in 100..599)

        with(outs) {
            clear()
            put("HTTP/1.1 $statusCode ${statusCodeReasons[statusCode]}\r\n".encodeToByteArray())
            headers.forEach { (header, value) ->
                put("$header: $value\r\n".encodeToByteArray())
            }
            put("\r\n".encodeToByteArray())
        }
        channel.write(outs)
    }
}

// NOTE(Dan): Generally, it doesn't make sense to have a dependency on ktor. But we make an exception here since the
// entire API system currently does depend on it. It makes it a lot easier to use the same object for passing around.
// If this were to change in the core of UCloud, then this code will probably be unified somehow.
typealias HttpMethod = io.ktor.http.HttpMethod

interface HttpRequestHandler<AppData> {
    fun HttpClientSession<AppData>.handleRequest(
        method: HttpMethod,
        path: String,
        headers: List<Header>,
        payload: ByteBuffer,
    )
}

interface WebSocketRequestHandler<AppData> {
    fun HttpClientSession<AppData>.handleBinaryFrame(frame: ByteBuffer) {}
    fun HttpClientSession<AppData>.handleTextFrame(frame: String) {}
    fun HttpClientSession<AppData>.handleNewConnection() {}
    fun HttpClientSession<AppData>.handleClosedConnection() {}
}

fun ByteBuffer.findDelimiter(delim: Byte): Int {
    for (i in readerIndex until writerIndex) {
        if (get(i) == delim) return i
    }
    return -1
}

fun ByteBuffer.readUntilDelimiter(delim: Byte): String? {
    val position = findDelimiter(delim)
    if (position != -1) {
        val size = position - readerIndex
        val bytes = ByteArray(size)
        get(readerIndex, bytes)
        readerIndex = position + 1
        return bytes.decodeToString()
    }
    return null
}

class EndOfStreamException : RuntimeException("End of stream has been reached")

fun SocketChannel.readUntilDelimiter(delim: Byte, buffer: ByteBuffer): String {
    val inBuffer = buffer.readUntilDelimiter(delim)
    if (inBuffer != null) {
        return inBuffer
    }

    while (true) {
        var writerSpaceRemaining = buffer.writerSpaceRemaining()
        if (writerSpaceRemaining == 0) {
            buffer.compact()
            writerSpaceRemaining = buffer.writerSpaceRemaining()
        }

        val bytesRead = read(fd, buffer.rawMemoryPinned.addressOf(buffer.writerIndex), writerSpaceRemaining.toULong())
        if (bytesRead <= 0) throw EndOfStreamException()
        buffer.writerIndex += bytesRead.toInt()

        val maybeLine = buffer.readUntilDelimiter(delim)
        if (maybeLine != null) return maybeLine
    }
}

inline fun SocketChannel.readAtLeast(minimumBytes: Int, buffer: ByteBuffer) {
    while (buffer.readerRemaining() < minimumBytes) {
        var writerSpaceRemaining = buffer.writerSpaceRemaining()
        if (writerSpaceRemaining == 0) {
            buffer.compact()
            writerSpaceRemaining = buffer.writerSpaceRemaining()
        }
        val bytesRead = read(fd, buffer.rawMemoryPinned.addressOf(buffer.writerIndex), writerSpaceRemaining.toULong())
        if (bytesRead <= 0) throw EndOfStreamException()
        buffer.writerIndex += bytesRead.toInt()
    }
}

private val shaPrefix = secureRandomLong().toString(16)
private var sha1acc = atomic(0)

fun sha1(data: ByteArray): ByteArray {
    // TODO(Dan): Link against OpenSSL instead of calling out to a executable
    val digest = ByteArray(20)
    val filePath = "/tmp/sha1_$shaPrefix${sha1acc.getAndIncrement()}"
    val file = NativeFile.open(filePath, readOnly = false, mode = "600".toInt(8))
    file.writeData(data)
    file.close()

    val computed = buildCommand("/usr/bin/sha1sum") {
        addArg(filePath)
    }.executeToText().stdout.substringBefore(' ')

    unlink(filePath)

    computed.chunked(2).forEachIndexed { index, s ->
        val byte = s.toInt(16).toByte()
        digest[index] = byte
    }

    return digest
}

const val NEW_LINE_DELIM = '\n'.code.toByte()

// TODO(Dan): Needed for file uploads, but we should really just send partial payloads
@ThreadLocal private val readBuffer = allocateDirect(1024 * 1024 * 32)

@OptIn(ExperimentalUnsignedTypes::class)
fun <AppData> startServer(
    port: Int,
    appDataFactory: () -> AppData,
    httpRequestHandler: HttpRequestHandler<AppData>? = null,
    webSocketRequestHandler: WebSocketRequestHandler<AppData>? = null,
) = memScoped {
    val log = Logger("Server")
    val websocketGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    val serverHandle = socket(AF_INET, SOCK_STREAM, 0)
    if (serverHandle <= 0) error("Could not bind to :$port")

    val trueSockValue = intArrayOf(1).pin()
    setsockopt(serverHandle, SOL_SOCKET, SO_REUSEADDR, trueSockValue.addressOf(0), 4)

    val socketAddress = alloc<sockaddr_in>()
    socketAddress.sin_family = AF_INET.toUShort()
    socketAddress.sin_addr.s_addr = htonl(INADDR_ANY)
    socketAddress.sin_port = htons(port.toUShort())
    val bindStatus = bind(serverHandle, socketAddress.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt())
    if (bindStatus < 0) error("Could not bind to :$port")

    if (listen(serverHandle, 1024) == -1) {
        error("Could not bind to :$port")
    }

    while (true) {
        val clientLength = uintArrayOf(0U).pin()
        val clientAddress = alloc<sockaddr_in>()
        val clientHandle = accept(serverHandle, clientAddress.ptr.reinterpret(), clientLength.addressOf(0))
        if (clientHandle == -1) {
            error("Failed to accept connection")
        }

        setsockopt(clientHandle, IPPROTO_TCP, TCP_NODELAY, trueSockValue.addressOf(0), 4)

        ProcessingScope.launch {
//            val readBuffer = allocateDirect(1024 * 8)
            val outputBuffer = allocateDirect(1024 * 8)

            // NOTE(Dan): The fragmentation buffer is required if the client decides to fragment messages earlier
            // than 8K. We don't use the fragmentation buffer unless the message is actually fragmented, allowing
            // us to get closer to 0 copies.
            val fragmentationBuffer = allocateDirect(1024 * 8)
            var fragmentationOpcode: WebSocketOpCode? = null

            val rawClient = SocketChannel(clientHandle)
            val client = HttpClientSession(rawClient, outputBuffer, appDataFactory())

            try {
                while (true) {
                    val requestLine = rawClient.readUntilDelimiter(NEW_LINE_DELIM, readBuffer)
                    val tokens = requestLine.split(" ")
                    if (tokens.size < 3 || !requestLine.endsWith("HTTP/1.1\r")) {
                        break
                    }

                    val method = tokens.first().uppercase()
                    val parsedMethod = HttpMethod.parse(method)
                    val path = tokens.getOrNull(1) ?: break

                    val requestHeaders = ArrayList<Header>()
                    while (true) {
                        val headerLine = rawClient.readUntilDelimiter(NEW_LINE_DELIM, readBuffer)
                        if (headerLine == "\r") break
                        requestHeaders.add(
                            Header(
                                headerLine.substringBefore(':'),
                                headerLine.substringAfter(':').trim()
                            )
                        )
                    }

                    if (webSocketRequestHandler != null &&
                        requestHeaders.any { it.header.equals("Upgrade", true) && it.value == "websocket" }
                    ) {
                        log.info("WS $path")
                        // The following headers are required to be present
                        val key = requestHeaders.find { it.header.equals("Sec-WebSocket-Key", true) }
                        val origin = requestHeaders.find { it.header.equals("Origin", true) }
                        val version = requestHeaders.find { it.header.equals("Sec-WebSocket-Version", true) }

                        if (key == null || version == null) {
                            log.debug("Missing '$key' or '$version'")
                            client.sendHttpResponse(400, defaultHeaders())
                            break
                        }

                        // We only speak WebSocket as defined in RFC 6455
                        if (!version.value.split(",").map { it.trim() }.contains("13")) {
                            client.sendHttpResponse(
                                400, defaultHeaders() + listOf(
                                    Header("Sec-WebSocket-Version", "13")
                                )
                            )
                            break
                        }

                        val responseHeaders = ArrayList<Header>()

                        // Prove to the client that we did in fact receive the request
                        responseHeaders.add(
                            Header(
                                "Sec-WebSocket-Accept",
                                base64Encode(
                                    sha1((key.value + websocketGuid).encodeToByteArray())
                                )
                            )
                        )

                        // Yes, we really want to upgrade this connection.
                        responseHeaders.add(Header("Connection", "Upgrade"))
                        responseHeaders.add(Header("Upgrade", "websocket"))

                        client.sendHttpResponse(101, defaultHeaders() + responseHeaders)

                        fun handleFrame(
                            fin: Boolean,
                            opcode: WebSocketOpCode?,
                            payload: ByteBuffer,
                        ): Boolean {
                            if (!fin || opcode == WebSocketOpCode.CONTINUATION) {
                                if (opcode !== WebSocketOpCode.CONTINUATION) {
                                    // First frame has !fin and opcode != CONTINUATION
                                    // Remaining frames will have opcode CONTINUATION
                                    // Last frame will have fin and opcode CONTINUATION
                                    fragmentationBuffer.clear()
                                }

                                if (fragmentationBuffer.writerSpaceRemaining() < payload.readerRemaining()) {
                                    log.info("Dropping connection. Packet size exceeds limit.")
                                    return true
                                }

                                fragmentationBuffer.put(payload)

                                if (!fin) return false

                                return handleFrame(true, fragmentationOpcode, fragmentationBuffer)
                            }

                            when (opcode) {
                                WebSocketOpCode.TEXT -> {
                                    with(client) {
                                        with(webSocketRequestHandler) {
                                            val buffer = ByteArray(payload.readerRemaining())
                                            payload.get(payload.readerIndex, buffer)
                                            payload.readerIndex += buffer.size
                                            handleTextFrame(buffer.decodeToString())
                                        }
                                    }
                                }

                                WebSocketOpCode.BINARY -> {
                                    with(client) {
                                        with(webSocketRequestHandler) {
                                            handleBinaryFrame(payload)
                                        }
                                    }
                                }

                                WebSocketOpCode.PING -> {
                                    client.sendWebsocketFrame(WebSocketOpCode.PONG, payload)
                                }

                                else -> {
                                    log.info("Type: $opcode")
                                }
                            }
                            return false
                        }

                        val maskingKey = UByteArray(4)
                        messageLoop@ while (!client.closing) {
                            // NOTE(Dan): Make sure we can read the initial byte and maskAndPayload
                            rawClient.readAtLeast(2, readBuffer)

                            val initialByte = readBuffer.getUnsigned(readBuffer.readerIndex++).toInt()

                            val fin = (initialByte and (0x01 shl 7)) != 0
                            // We don't care about rsv1,2,3
                            val opcode = WebSocketOpCode.values().find { it.opcode == (initialByte and 0x0F) }

                            val maskAndPayload = readBuffer.getUnsigned(readBuffer.readerIndex++).toInt()
                            val mask = (maskAndPayload and (0x01 shl 7)) != 0
                            val payloadLength: Long = run {
                                val payloadB1 = (maskAndPayload and 0b01111111)
                                when {
                                    payloadB1 < 126 -> return@run payloadB1.toLong()
                                    payloadB1 == 126 -> {
                                        rawClient.readAtLeast(2, readBuffer)
                                        readBuffer.getShort(readBuffer.readerIndex).also {
                                            readBuffer.readerIndex += 2
                                        }.toLong()
                                    }
                                    payloadB1 == 127 -> {
                                        rawClient.readAtLeast(8, readBuffer)
                                        readBuffer.getLong(readBuffer.readerIndex).also {
                                            readBuffer.readerIndex += 8
                                        }
                                    }
                                    else -> throw IllegalStateException()
                                }
                            }

                            if (mask) {
                                rawClient.readAtLeast(4, readBuffer)
                                maskingKey[0] = readBuffer.getUnsigned(readBuffer.readerIndex++)
                                maskingKey[1] = readBuffer.getUnsigned(readBuffer.readerIndex++)
                                maskingKey[2] = readBuffer.getUnsigned(readBuffer.readerIndex++)
                                maskingKey[3] = readBuffer.getUnsigned(readBuffer.readerIndex++)
                            }

                            if (payloadLength > readBuffer.capacity() - 16) {
                                log.info("Too large payload sent to server: $payloadLength")
                                rawClient.close()
                                break
                            }

                            if (payloadLength > readBuffer.writerSpaceRemaining()) {
                                readBuffer.compact()
                            }

                            rawClient.readAtLeast(payloadLength.toInt(), readBuffer)

                            if (mask) {
                                // TODO We should be able to do a 4/8 bytes at a time.
                                val offset = readBuffer.readerIndex
                                for (idx in 0 until payloadLength.toInt()) {
                                    val index = idx + offset
                                    readBuffer.put(
                                        index,
                                        (readBuffer.getUnsigned(index) xor maskingKey[idx % 4]).toByte()
                                    )
                                }
                            }

                            val finalReadIndex = readBuffer.readerIndex + payloadLength.toInt()
                            val savedWriterIndex = readBuffer.writerIndex
                            readBuffer.writerIndex = finalReadIndex

                            if (handleFrame(fin, opcode, readBuffer)) {
                                log.debug("just done")
                                break@messageLoop
                            }

                            // Move the read index beyond the payload
                            readBuffer.readerIndex = finalReadIndex
                            readBuffer.writerIndex = savedWriterIndex
                        }
                    } else {
                        val contentLength = requestHeaders.find {
                            it.header.equals("Content-Length", ignoreCase = true)
                        }

                        val transferEncoding = requestHeaders.find {
                            it.header.equals("Transfer-Encoding", ignoreCase = true)
                        }

                        if (transferEncoding != null && !transferEncoding.value.equals("identity", ignoreCase = true)) {
                            log.warn("Received a message which we cannot handle ($transferEncoding)")
                            client.sendHttpResponse(400, defaultHeaders())
                            // NOTE(Dan): Do not attempt to read anything else from the stream as we don't know when the
                            // next message begins.
                            client.closing = true
                            break
                        }

                        val payloadSize = if (contentLength == null) {
                            0
                        } else {
                            contentLength.value.toIntOrNull()
                        }

                        if (payloadSize == null || payloadSize < 0) {
                            log.debug("Received request payload is invalid: $contentLength")
                            client.sendHttpResponse(400, defaultHeaders())
                            client.closing = true
                            break
                        }

                        if (payloadSize > 0) {
                            // Make sure we read the payload
                            rawClient.readAtLeast(payloadSize, readBuffer)
                        }

                        val finalReadIndex = readBuffer.readerIndex + payloadSize
                        val savedWriterIndex = readBuffer.writerIndex
                        readBuffer.writerIndex = finalReadIndex

                        val shouldClose = requestHeaders.any {
                            it.header.equals("Connection", ignoreCase = true) &&
                                it.value.equals("close", ignoreCase = true)
                        }

                        if (httpRequestHandler != null) {
                            with(httpRequestHandler) {
                                with(client) {
                                    handleRequest(parsedMethod, path, requestHeaders, readBuffer)
                                }
                            }
                        }

                        readBuffer.readerIndex = finalReadIndex
                        readBuffer.writerIndex = savedWriterIndex

                        if (shouldClose) {
                            client.closing = true
                            break
                        }
                    }
                }
            } catch (ex: EndOfStreamException) {
                // Ignore
            } catch (ex: Throwable) {
                log.warn(ex.stackTraceToString())
            } finally {
                client.closing = true
                runCatching { rawClient.close() }
            }
        }
    }
}

enum class WebSocketOpCode(val opcode: Int) {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CONNECTION_CLOSE(0x8),
    PING(0x9),
    PONG(0xA)
}

fun defaultHeaders(payloadSize: Long = 0): List<Header> = listOf(
    dateHeader(),
    Header("Content-Length", payloadSize.toString())
)

private fun dateHeader(): Header {
    memScoped {
        val time = longArrayOf(time(null)).pin()
        val st = gmtime(time.addressOf(0)) ?: error("Could not retrieve the time")
        val tm = st.pointed
        return Header("Date", buildString {
            append(
                when(tm.tm_wday) {
                    0 -> "Sun"
                    1 -> "Mon"
                    2 -> "Tue"
                    3 -> "Wed"
                    4 -> "Thu"
                    5 -> "Fri"
                    6 -> "Sat"
                    else -> "Mon"
                }
            )

            append(", ")
            append(tm.tm_mday.toString().padStart(2, '0'))
            append(" ")

            append(
                when (tm.tm_mon) {
                    0 -> "Jan"
                    1 -> "Feb"
                    2 -> "Mar"
                    3 -> "Apr"
                    4 -> "May"
                    5 -> "Jun"
                    6 -> "Jul"
                    7 -> "Aug"
                    8 -> "Sep"
                    9 -> "Oct"
                    10 -> "Nov"
                    11 -> "Dec"
                    else -> "Jan"
                }
            )

            append(" ")
            append(tm.tm_year + 1900)
            append(" ")
            append(tm.tm_hour.toString().padStart(2, '0'))
            append(":")
            append(tm.tm_min.toString().padStart(2, '0'))
            append(":")
            append(tm.tm_sec.toString().padStart(2, '0'))
            append(" GMT")
        })
    }
}

data class Header(val header: String, val value: String)
