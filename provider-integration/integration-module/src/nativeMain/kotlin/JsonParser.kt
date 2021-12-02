package dk.sdu.cloud

sealed class JsonStreamElement {
    object ObjectStart : JsonStreamElement()
    object ObjectEnd : JsonStreamElement()
    object ArrayStart : JsonStreamElement()
    object ArrayEnd : JsonStreamElement()
    object Null : JsonStreamElement()

    data class Text(val value: String) : JsonStreamElement()
    data class Bool(val value: Boolean) : JsonStreamElement()
    data class Number(val value: Double) : JsonStreamElement()
    data class Property(val key: String, val element: JsonStreamElement) : JsonStreamElement()
}

class JsonParser(val data: String) {
    private val cursor = Cursor(data)
    private val contextStack = ArrayDeque<JsonStreamElement>()
    private var requireEndOfContext = false

    private fun endContext() {
        contextStack.removeFirst()
        val newHead = contextStack.firstOrNull()
        if (newHead == null) {
            requireEndOfContext = false
        } else {
            cursor.consumeWhitespace()
            if (cursor.peekToken() == ',') {
                cursor.pos++
                requireEndOfContext = false
            } else {
                requireEndOfContext = true
            }
        }
    }

    fun skipCurrentContext() {
        var depth = 1
        while (true) {
            val nextTok = nextToken() ?: throw ParsingException("Unexpected EOF")
            if (nextTok == JsonStreamElement.ArrayEnd || nextTok == JsonStreamElement.ObjectEnd) {
                depth--
                if (depth == 0) break
            } else if (nextTok == JsonStreamElement.ArrayStart || nextTok == JsonStreamElement.ObjectStart) {
                depth++
            }
        }
    }

    fun nextToken(): JsonStreamElement? {
        val context = contextStack.firstOrNull()

        cursor.consumeWhitespace()
        if (cursor.pos >= cursor.size) {
            return null
        }

        if (requireEndOfContext) {
            check(context != null)
            return if (context == JsonStreamElement.ObjectStart) {
                cursor.consumeWhitespace()
                cursor.consumeToken("}")
                endContext()
                JsonStreamElement.ObjectEnd
            } else if (context == JsonStreamElement.ArrayStart) {
                cursor.consumeWhitespace()
                cursor.consumeToken("]")
                endContext()
                JsonStreamElement.ArrayEnd
            } else {
                error("Should not happen")
            }
        }

        var property: String? = null
        if (context == JsonStreamElement.ObjectStart) {
            cursor.consumeWhitespace()
            if (cursor.peekToken() == '}') {
                requireEndOfContext = true
                return nextToken()
            }

            property = cursor.consumeString()
            cursor.consumeWhitespace()
            cursor.consumeToken(":")
        }

        cursor.consumeWhitespace()
        val nextToken = cursor.peekToken()

        val elem = when {
            nextToken == '{' -> {
                cursor.pos++
                contextStack.addFirst(JsonStreamElement.ObjectStart)
                JsonStreamElement.ObjectStart
            }

            nextToken == '[' -> {
                cursor.pos++
                contextStack.addFirst(JsonStreamElement.ArrayStart)
                JsonStreamElement.ArrayStart
            }

            nextToken.isDigit() -> {
                JsonStreamElement.Number(cursor.consumeNumber())
            }

            nextToken == 't' -> {
                cursor.consumeToken("true")
                JsonStreamElement.Bool(true)
            }

            nextToken == 'f' -> {
                cursor.consumeToken("false")
                JsonStreamElement.Bool(false)
            }

            nextToken == 'n' -> {
                cursor.consumeToken("null")
                JsonStreamElement.Null
            }

            nextToken == '"' -> {
                // Consume text
                JsonStreamElement.Text(cursor.consumeString())
            }

            nextToken == ']' -> {
                cursor.pos++
                endContext()
                return JsonStreamElement.ArrayEnd // NOTE(Dan): We need to end early
            }

            else -> throw ParsingException("Unexpected token: $nextToken")
        }

        return if (context == JsonStreamElement.ArrayStart) {
            cursor.consumeWhitespace()
            if (cursor.peekToken() == ',') {
                cursor.pos++
            } else {
                if (elem != JsonStreamElement.ArrayStart && elem != JsonStreamElement.ObjectStart) {
                    requireEndOfContext = true
                }
            }

            elem
        } else if (property != null)  {
            cursor.consumeWhitespace()
            if (cursor.peekToken() == ',') {
                cursor.pos++
            } else {
                if (elem != JsonStreamElement.ArrayStart && elem != JsonStreamElement.ObjectStart) {
                    requireEndOfContext = true
                }
            }
            JsonStreamElement.Property(property, elem)
        } else {
            elem
        }
    }
}

class Cursor(val data: String) {
    val size = data.length
    var pos: Int = 0

    fun consumeWhitespace() {
        while (pos < size) {
            val char = data[pos]
            if (char.isWhitespace()) {
                pos++
            } else {
                break
            }
        }
    }

    inline fun peekToken(): Char {
        return data[pos]
    }

    fun consumeToken(token: String) {
        var i = 0
        while (pos + i < size && i < token.length) {
            if (token[i] != data[pos + i]) {
                throw ParsingException("Expected '$token' but did not find it")
            }
            i++
        }

        if (token.length != i) {
            throw ParsingException("Expected '$token' but did not find it")
        }

        pos += token.length
    }

    fun consumeNumber(): Double {
        val builder = StringBuilder()
        var hasSeenDot = false
        while (pos < size) {
            val char = data[pos]
            if (char.isDigit()) {
                builder.append(char)
            } else if (char == '.') {
                if (hasSeenDot) throw ParsingException("Expected number")
                hasSeenDot = true
                builder.append(char)
            } else {
                break
            }
            pos++
        }
        return builder.toString().toDouble()
    }

    fun consumeString(): String {
        val builder = StringBuilder()
        consumeToken("\"")
        while (pos < size) {
            val char = data[pos++]
            if (char == '"') {
                return builder.toString()
            } else {
                builder.append(char)
            }
        }
        throw ParsingException("Unterminated string")
    }
}

class ParsingException(message: String) : RuntimeException(message)