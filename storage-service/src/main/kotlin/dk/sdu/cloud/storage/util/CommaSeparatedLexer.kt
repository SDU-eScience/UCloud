package dk.sdu.cloud.storage.util

class CommaSeparatedLexer(line: String = "") {
    private var _line = line
    private var chars = _line.toCharArray()

    // Used by several clients for retrieving the remaining string and reporting errors
    var cursor = 0
        private set

    var line: String
        get() = _line
        set(value) {
            _line = value
            chars = _line.toCharArray()
            cursor = 0
        }

    val remaining: String
        get() = _line.substring(cursor)

    fun readToken(): String {
        val builder = StringBuilder()
        while (cursor < chars.size) {
            val c = chars[cursor++]
            if (c == ',') break
            builder.append(c)
        }
        return builder.toString()
    }


    override fun toString() = "CommaSeparatedLexer($_line, $cursor, ${_line.substring(cursor)})"
}