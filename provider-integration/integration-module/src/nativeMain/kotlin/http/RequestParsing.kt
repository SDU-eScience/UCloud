package dk.sdu.cloud.http

import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpQueryParameter
import dk.sdu.cloud.calls.client.urlEncode
import dk.sdu.cloud.calls.http
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

data class ParsedQueryString(val attributes: Map<String, List<String>>) {
    companion object {
        fun parse(query: String): ParsedQueryString {
            val map = HashMap<String, List<String>>()
            query.removePrefix("?").split("&").forEach { part ->
                if (part.contains("=")) {
                    runCatching {
                        val name = decodeUrlComponent(part.substringBefore('='))
                        val value = decodeUrlComponent(part.substringAfter('='))

                        map[name] = (map[name] ?: emptyList()) + value
                    }
                } else {
                    runCatching {
                        val name = decodeUrlComponent(part)
                        map[name] = (map[name] ?: emptyList()) + ""
                    }
                }
            }
            return ParsedQueryString(map)
        }
    }
}

fun encodeQueryParamsToString(queryPathMap: Map<String, List<String>>): String {
    return queryPathMap
        .flatMap { param ->
            param.value.map { v -> urlEncode(param.key) + "=" + urlEncode(v) }
        }
        .joinToString("&")
        .takeIf { it.isNotEmpty() }
        ?.let { "?$it" } ?: ""
}

// https://stackoverflow.com/a/52378025
private fun decodeUrlComponent(str: String): String {
    val length: Int = str.length
    val bytes = ByteArray(length / 3)
    val builder = StringBuilder(length)

    var i = 0
    while (i < length) {
        var c: Char = str[i]
        if (c != '%') {
            builder.append(c)
            i += 1
        } else {
            var j = 0
            do {
                var h: Int = str[i + 1].code
                var l: Int = str[i + 2].code
                i += 3
                h = (h - '0'.code)
                if (h >= 10) {
                    h = (h or ' '.code)
                    h -= ('a'.code - '0'.code)
                    require(h < 6)
                    h += 10
                }
                l -= '0'.code
                if (l >= 10) {
                    l = l or ' '.code
                    l -= ('a'.code - '0'.code)
                    require(l < 6)
                    l += 10
                }
                bytes[j++] = (h shl 4 or l).toByte()
                if (i >= length) break
                c = str[i]
            } while (c == '%')
            builder.append(bytes.decodeToString(0, j))
        }
    }

    return builder.toString()
}

@OptIn(ExperimentalSerializationApi::class)
class ParamsParsing(
    queryString: String,
    private val call: CallDescription<*, *, *>,
) : AbstractDecoder() {
    private var lastReadIdx: Int = -1
    private var lastRead: String? = null
    private val parameters = call.http.params?.parameters ?: emptyList()
    private var elementIndex = 0
    private var parsedQuery = ParsedQueryString.parse(queryString)
    override val serializersModule: SerializersModule = EmptySerializersModule
    private val value: String?
        get() {
            if (lastReadIdx == elementIndex) {
                return lastRead
            } else {
                when (val param = parameters[elementIndex - 1]) {
                    is HttpQueryParameter.Property<*> -> {
                        val attributes = parsedQuery.attributes[param.property]
                        if (attributes != null) {
                            lastRead = attributes[0]
                        } else {
                            lastRead = null
                        }
                        lastReadIdx = elementIndex
                    }
                }
            }
            return lastRead
        }


    override fun decodeBoolean(): Boolean {
        return value == "true"
    }

    override fun decodeByte(): Byte {
        return value?.toByteOrNull() ?: throw SerializationException("$value is not a valid byte")
    }

    override fun decodeChar(): Char {
        return value?.firstOrNull() ?: throw SerializationException("$value is not a valid char")
    }

    override fun decodeDouble(): Double {
        return value?.toDoubleOrNull() ?: throw SerializationException("$value is not a valid double")
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val firstTry = enumDescriptor.elementNames.indexOf(value).takeIf { it != -1 }
        if (firstTry != null) return firstTry
        return enumDescriptor.elementNames.indexOfFirst { it.equals(value, ignoreCase = true) }.takeIf { it != -1 }
            ?: throw SerializationException("$value is not a valid enum")
    }

    override fun decodeFloat(): Float {
        return value?.toFloatOrNull() ?: throw SerializationException("$value is not a valid float")
    }

    override fun decodeInt(): Int {
        return value?.toIntOrNull() ?: throw SerializationException("$value is not a valid int")
    }

    override fun decodeLong(): Long {
        return value?.toLongOrNull() ?: throw SerializationException("$value is not a valid long")
    }

    override fun decodeShort(): Short {
        return value?.toShortOrNull() ?: throw SerializationException("$value is not a valid short")
    }

    override fun decodeString(): String {
        return value ?: throw SerializationException("$value was string")
    }

    override fun decodeNotNullMark(): Boolean {
        return value != null
    }

    override fun decodeNull(): Nothing? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == parameters.size) return CompositeDecoder.DECODE_DONE
        return when (val param = parameters[elementIndex++]) {
            is HttpQueryParameter.Property<*> -> descriptor.getElementIndex(param.property)
        }
    }
}
