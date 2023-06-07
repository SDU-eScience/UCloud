fun main() {
    val cursor = Cursor(
        "stdin",
        """
            /// A generic error message
            ///
            /// UCloud uses HTTP status codes for all error messages...
            @Stable
            record CommonErrorMessage {
                why: String,
                errorCode: String?,
            }

            @Experimental(level = "ALPHA")
            tagged record AppParameterValue {
                @Tag(text = "file", ordinal = 0)
                record File : AppParameterValue {
                    path: String,
                    readOnly: Boolean,
                }
                
                @Tag(text = "boolean", ordinal = 1)
                record Bool : AppParameterValue {
                    value: Boolean,
                }
            }

            @Stable(version = 1)
            enum JobState {
                IN_QUEUE = 0,
                RUNNING = 1,
                CANCELING = 2,
                SUCCESS = 3,
                FAILURE = 4,
                EXPIRED = 5,
                SUSPENDED = 6,
            }

            @Stable(version = 1)
            record JobStatus {
                state: JobState,
                startedAt: Long?,
                expiresAt: Long?,
                // ...
            }

        """.trimIndent()
    )

    println(Parser(cursor).parse())
//    repeat(60) {
//        println(Lexer.consume(cursor))
//    }
}

data class Location(
    var fileIdentifier: String,
    var column: Int,
    var line: Int
)

class Cursor(
    private val fileIdentifier: String,
    private val data: String
) {
    private var pointer = 0
    val location = Location(fileIdentifier, 1, 1)
    private var mark = 0

    fun mark() {
        this.mark = pointer
    }

    fun restore() {
        this.pointer = 0
        if (mark > 0) consume(mark)
    }

    fun consumeWhitespace() {
        while (!isEof()) {
            if (!peekSingle().isWhitespace()) break
            consume(1)
        }
    }

    fun consumeRestOfLine(): String {
        val builder = StringBuilder()
        while (!isEof()) {
            if (peekSingle() == '\n') break
            builder.append(consume(1))
        }
        return builder.toString()
    }

    fun peekSingle(): Char {
        return data[pointer]
    }

    fun peek(count: Int): String {
        return data.substring(pointer, pointer + count)
    }

    fun consume(count: Int): String {
        val result = data.substring(pointer, pointer + count)
        for (char in result) {
            if (char == '\n') {
                location.line++
                location.column = 1
            } else {
                location.column++
            }
        }
        pointer += count
        return result
    }

    fun isEof(): Boolean {
        return pointer >= data.length
    }
}

sealed class Token {
    abstract val location: Location

    data class Identifier(
        override val location: Location,
        val identifier: String,
    ) : Token()

    data class Comment(
        override val location: Location,
        val comment: String,
    ) : Token()

    data class Symbol(
        override val location: Location,
        val type: Type,
    ) : Token() {
        enum class Type {
            AT_SYMBOL,
            QUESTION_MARK,
            COLON,
            COMMA,
            EQUALS,
            CURLY_START,
            CURLY_END,
            SQUARE_START,
            SQUARE_END,
            PARENTHESIS_START,
            PARENTHESIS_END,
        }
    }

    data class Text(
        override val location: Location,
        val text: String,
    ) : Token()

    data class Integer(
        override val location: Location,
        val integer: Int,
    ) : Token()
}

object Lexer {
    fun consume(cursor: Cursor, skipComment: Boolean = true): Token? {
        cursor.consumeWhitespace()
        if (cursor.isEof()) return null

        val location = cursor.location.copy()
        when (cursor.peekSingle()) {
            '@' -> return Token.Symbol(location, Token.Symbol.Type.AT_SYMBOL).also { cursor.consume(1) }
            '?' -> return Token.Symbol(location, Token.Symbol.Type.QUESTION_MARK).also { cursor.consume(1) }
            ':' -> return Token.Symbol(location, Token.Symbol.Type.COLON).also { cursor.consume(1) }
            ',' -> return Token.Symbol(location, Token.Symbol.Type.COMMA).also { cursor.consume(1) }
            '=' -> return Token.Symbol(location, Token.Symbol.Type.EQUALS).also { cursor.consume(1) }
            '(' -> return Token.Symbol(location, Token.Symbol.Type.PARENTHESIS_START).also { cursor.consume(1) }
            ')' -> return Token.Symbol(location, Token.Symbol.Type.PARENTHESIS_END).also { cursor.consume(1) }
            '[' -> return Token.Symbol(location, Token.Symbol.Type.SQUARE_START).also { cursor.consume(1) }
            ']' -> return Token.Symbol(location, Token.Symbol.Type.SQUARE_END).also { cursor.consume(1) }
            '{' -> return Token.Symbol(location, Token.Symbol.Type.CURLY_START).also { cursor.consume(1) }
            '}' -> return Token.Symbol(location, Token.Symbol.Type.CURLY_END).also { cursor.consume(1) }
            '\"' -> {
                cursor.consume(1)
                val builder = StringBuilder()
                while (!cursor.isEof()) {
                    val next = cursor.consume(1)
                    if (next == "\"") break
                    builder.append(next)
                }
                return Token.Text(location, builder.toString())
            }
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                val builder = StringBuilder()
                while (!cursor.isEof()) {
                    val next = cursor.peekSingle()
                    if (!next.isDigit()) break
                    builder.append(cursor.consume(1))
                }
                return Token.Integer(location, builder.toString().toInt())
            }
        }

        when (cursor.peek(2)) {
            "//" -> {
                val builder = StringBuilder()
                while (!cursor.isEof()) {
                    cursor.consume(2)
                    builder.appendLine(cursor.consumeRestOfLine())

                    cursor.consumeWhitespace()
                    if (cursor.isEof() || cursor.peek(2) != "//") break
                }

                if (skipComment) return consume(cursor, skipComment)
                return Token.Comment(location, builder.toString().trim())
            }
        }

        val builder = StringBuilder()
        while (!cursor.isEof()) {
            val next = cursor.peekSingle()
            if (next.isJavaIdentifierPart()) {
                cursor.consume(1)
                builder.append(next)
            } else {
                break
            }
        }

        return Token.Identifier(location, builder.toString())
    }
}

class Parser(private val cursor: Cursor) {
    sealed class Node {
        abstract val location: Location
        data class Annotation(
            override val location: Location,
            val name: String,
            val attributes: List<Pair<String, Token>>,
        ) : Node() {
            override fun toString(): String = buildString {
                appendLine("Node at $location")
                appendLine("name = $name")
                appendLine("attributes = $attributes")
            }
        }

        data class Enumeration(
            override val location: Location,
            val name: String,
            val annotations: List<Annotation>,
            val entries: List<Pair<String, Int>>,
        ) : Node() {
            override fun toString(): String = buildString {
                appendLine("Node at $location")
                appendLine("name = $name")
                for (annotation in annotations) {
                    appendLine("annotation = $annotation")
                }
                appendLine("entries = $entries")
            }
        }

        data class Record(
            override val location: Location,
            val isTagged: Boolean,
            val name: String,
            val implements: String?,
            val annotations: List<Annotation>,
            val properties: List<Property>,
            val nested: List<Record>,
        ) : Node() {
            override fun toString(): String = buildString {
                appendLine("Record at $location")
                appendLine("name = $name")
                appendLine("implements = $implements")
                appendLine("isTagged = $isTagged")

                for (annotation in annotations) {
                    appendLine("annotation = $annotation")
                }

                for (record in nested) {
                    appendLine("Nested record: $record")
                }

                for (property in properties) {
                    appendLine("property = $property")
                }
            }
        }

        data class Property(
            override val location: Location,
            val annotations: List<Annotation>,
            val name: String,
            val type: String,
            val optional: Boolean,
        ) : Node() {
            override fun toString(): String = buildString {
                appendLine("Property at $location")
                appendLine("name = $name")
                appendLine("type = $type")
                appendLine("optional = $optional")
                for (annotation in annotations) {
                    appendLine("annotation = $annotation")
                }
            }
        }
    }

    data class Ast(
        val packageName: String?,
        val records: List<Node.Record>,
        val enumerations: List<Node.Enumeration>,
    ) {
        override fun toString(): String = buildString {
            appendLine("package $packageName")
            for (record in records) {
                appendLine(record.toString())
            }
            for (enum in enumerations) {
                appendLine(enum.toString())
            }
        }
    }

    fun parse(): Ast {
        var packageName: String? = null

        val initial = Lexer.consume(cursor)
        var next: Token? = initial
        if (initial is Token.Identifier && initial.identifier == "package") {
            val packageToken = Lexer.consume(cursor)
            if (packageToken !is Token.Identifier) {
                reportError(packageToken?.location, "Expected a package name. For example: package dk.sdu.cloud")
            }

            packageName = packageToken.identifier
            next = Lexer.consume(cursor)
        }

        var docComment: String? = null
        var annotations = ArrayList<Node.Annotation>()
        val records = ArrayList<Node.Record>()
        val enumerations = ArrayList<Node.Enumeration>()
        while (true) {
            when {
                next is Token.Symbol && next.type == Token.Symbol.Type.AT_SYMBOL -> {
                    annotations.add(parseAnnotation())
                }

                next is Token.Identifier && (next.identifier == "record" || next.identifier == "tagged") -> {
                    records.add(parseRecord(next, annotations))
                    annotations = ArrayList()
                }

                next is Token.Identifier && next.identifier == "enum" -> {
                    enumerations.add(parseEnum(annotations))
                    annotations = ArrayList()
                }

                next is Token.Comment -> {
                    if (next.comment.startsWith("/")) {
                        docComment = next.comment.lineSequence().map { it.removePrefix("/").trim() }.joinToString("\n")
                    }
                }

                else -> {
                    reportError(next?.location, "Unexpected token: $next")
                }
            }

            if (cursor.isEof()) break
            next = Lexer.consume(cursor, skipComment = false) ?: break
        }

        return Ast(packageName, records, enumerations)
    }

    private fun parseAnnotation(): Node.Annotation {
        val initialTok = Lexer.consume(cursor)
        if (initialTok !is Token.Identifier) {
            reportError(initialTok?.location, "Expected an identifier for the annotation. For example: @Stable")
        }

        val annotationName = initialTok.identifier
        val fields = ArrayList<Pair<String, Token>>()

        cursor.mark()
        val next = Lexer.consume(cursor)
        if (next is Token.Symbol && next.type == Token.Symbol.Type.PARENTHESIS_START) {
            while (!cursor.isEof()) {
                val nameTok = Lexer.consume(cursor)
                if (nameTok is Token.Symbol) {
                    if (nameTok.type == Token.Symbol.Type.COMMA) continue
                    if (nameTok.type != Token.Symbol.Type.PARENTHESIS_END) {
                        reportError(nameTok.location, "Unexpected symbol. Did you mean to end the annotation with a ')'?")
                    }
                    break
                }

                val name = if (nameTok !is Token.Identifier) {
                    reportError(nameTok?.location, "Expected an identifier. For example: @Stable(version = 1)")
                } else {
                    nameTok.identifier
                }

                val equalsTok = Lexer.consume(cursor)
                if (equalsTok !is Token.Symbol || equalsTok.type != Token.Symbol.Type.EQUALS) {
                    reportError(equalsTok?.location, "Expected an equals sign. For example: @Stable(version = 1)")
                }

                val valueTok = Lexer.consume(cursor)
                if (!(valueTok is Token.Text || valueTok is Token.Integer)) {
                    reportError(valueTok?.location, "Expected either a string or an integer. For example: @Stable(version = 1)")
                }

                fields.add(Pair(name, valueTok))
            }
        } else {
            cursor.restore()
        }
        return Node.Annotation(initialTok.location, annotationName, fields)
    }

    private fun parseRecord(initialTok: Token.Identifier, annotations: List<Node.Annotation>): Node.Record {
        var isTagged = false
        var implements: String? = null
        if (initialTok.identifier == "tagged") {
            isTagged = true
            val recordTok = Lexer.consume(cursor)
            if (recordTok !is Token.Identifier || recordTok.identifier != "record") {
                reportError(recordTok?.location, "Expected 'record'. For example: tagged record Example {}")
            }
        }

        val nameTok = Lexer.consume(cursor)
        if (nameTok !is Token.Identifier) {
            reportError(nameTok?.location, "Expected a name for the record. For example: record Example {}")
        }

        val braceOrColon = Lexer.consume(cursor)
        if (braceOrColon !is Token.Symbol) reportError(braceOrColon?.location, "Expected start of record with '{'")
        if (braceOrColon.type == Token.Symbol.Type.COLON) {
            val implementsTok = Lexer.consume(cursor)
            if (implementsTok !is Token.Identifier) {
                reportError(
                    implementsTok?.location,
                    "Expected an identifier to determine which type this record inherits from."
                )
            }

            implements = implementsTok.identifier

            val braceTok = Lexer.consume(cursor)
            if (braceTok !is Token.Symbol || braceTok.type != Token.Symbol.Type.CURLY_START) {
                reportError(braceTok?.location, "Expected '{' to start the record.")
            }
        } else if (braceOrColon.type != Token.Symbol.Type.CURLY_START) {
            reportError(braceOrColon.location, "Expected '{' to start the record.")
        }

        val properties = ArrayList<Node.Property>()
        val nestedRecords = ArrayList<Node.Record>()
        var propAnnotations = ArrayList<Node.Annotation>()
        while (true) {
            val next = Lexer.consume(cursor)
            if (next is Token.Symbol && next.type == Token.Symbol.Type.AT_SYMBOL) {
                propAnnotations.add(parseAnnotation())
            } else if (next is Token.Symbol && next.type == Token.Symbol.Type.CURLY_END) {
                break
            } else if (next is Token.Identifier) {
                if (next.identifier == "record") {
                    nestedRecords.add(parseRecord(next, propAnnotations))
                } else {
                    properties.add(parseProperty(next, propAnnotations))
                }
                propAnnotations = ArrayList()
            } else {
                reportError(next?.location, "Unexpected token while parsing record")
            }
        }

        return Node.Record(
            initialTok.location,
            isTagged,
            nameTok.identifier,
            implements,
            annotations,
            properties,
            nestedRecords
        )
    }

    private fun parseProperty(initialTok: Token.Identifier, annotations: List<Node.Annotation>): Node.Property {
        var next: Token?
        next = initialTok
        val startOfProperty = next.location

        var isOptional = false
        val name = next.identifier

        next = Lexer.consume(cursor)
        if (next !is Token.Symbol || next.type != Token.Symbol.Type.COLON) {
            reportError(next?.location, "Expected colon to start the type. For example: `path: String,`")
        }

        next = Lexer.consume(cursor)
        if (next !is Token.Identifier) {
            reportError(next?.location, "Expected a type for the property. For example: `path: String,`")
        }

        val type = next.identifier

        next = Lexer.consume(cursor)
        if (next is Token.Symbol && next.type == Token.Symbol.Type.QUESTION_MARK) {
            isOptional = true
            next = Lexer.consume(cursor)
        }

        if (next !is Token.Symbol || next.type != Token.Symbol.Type.COMMA) {
            reportError(next?.location, "Expected a comma to end the property. For example: `path: String,`")
        }

        return Node.Property(startOfProperty!!, annotations, name, type, isOptional)
    }

    private fun parseEnum(annotations: List<Node.Annotation>): Node.Enumeration {
        val nameTok = Lexer.consume(cursor)
        if (nameTok !is Token.Identifier) {
            reportError(nameTok?.location, "Expected name of enum. Example: `enum Foo {}`")
        }

        val braceStart = Lexer.consume(cursor)
        if (braceStart !is Token.Symbol || braceStart.type != Token.Symbol.Type.CURLY_START) {
            reportError(braceStart?.location, "Expected '{' to start the enum")
        }

        val entries = ArrayList<Pair<String, Int>>()
        while (true) {
            val initial = Lexer.consume(cursor)
            if (initial is Token.Symbol && initial.type == Token.Symbol.Type.CURLY_END) break

            if (initial !is Token.Identifier) {
                reportError(initial?.location, "Expected identifier to start enum. Example: `FOO = 1,`")
            }

            val equalsTok = Lexer.consume(cursor)
            if (equalsTok !is Token.Symbol || equalsTok.type != Token.Symbol.Type.EQUALS) {
                reportError(equalsTok?.location, "Expected '=' to begin the ordinal of an enum. Example: `FOO = 1,`")
            }

            val ordinalTok = Lexer.consume(cursor)
            if (ordinalTok !is Token.Integer) {
                reportError(ordinalTok?.location, "Expected an integer to define the enum's ordinal. Example: `FOO = 1,`")
            }

            val commaTok = Lexer.consume(cursor)
            if (commaTok !is Token.Symbol || commaTok.type != Token.Symbol.Type.COMMA) {
                reportError(commaTok?.location, "Expected a comma to end the enum. This is mandatory even for the last element!")
            }

            entries.add(initial.identifier to ordinalTok.integer)
        }

        return Node.Enumeration(nameTok.location, nameTok.identifier, annotations, entries)
    }
}

fun reportError(location: Location?, message: String): Nothing = error("$location: $message")
