fun main() {
    /*
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
     */

    val cursor = Cursor(
        "stdin",
        """
            // @Stable(version = 1)
            // enum JobState {
            //     IN_QUEUE = 0,
            //     RUNNING = 1,
            //     CANCELING = 2,
            //     SUCCESS = 3,
            //     FAILURE = 4,
            //     EXPIRED = 5,
            //     SUSPENDED = 6,
            // }
            
            // record SmallExampleOfPrimitives {
            //     hund: Byte?,
            //     dog: Short?,
            //     fie: Int?,
            //     fiedog: Long?,
            //     isGood: Boolean?,
            //     
            //     u8: UByte?,
            //     u16: UShort?,
            //     u32: UInt?,
            //     u64: ULong?,
            // }
            
            tagged record AnInterface {
                myProperty: Int,
                
                @Tag(text = "impl1", ordinal = 0)
                record Impl1 : AnInterface {
                    myProperty: Int,
                    myOwnProperty: Short,
                }
                
                @Tag(text = "impl2", ordinal = 1)
                record Impl2 : AnInterface {
                    myProperty: Int,
                    someOtherProperty: String,
                }
            }
            
            record LinkedList2 {
                value: AnInterface,
                next: LinkedList2?,
            }
        """.trimIndent()
    )

    val ast = Parser(cursor).parse()
    val typeTable = buildTypeTable(listOf(ast))
    println(generateKotlinCode(ast, typeTable))
//    repeat(60) {
//        println(Lexer.consume(cursor))
//    }
}

// =====================================================================================================================
// Parsing
// =====================================================================================================================

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

// =====================================================================================================================
// Type table
// =====================================================================================================================
data class TypeTable(
    val typesByCanonicalName: Map<String, Type>,
) {
    fun lookupType(file: Parser.Ast, name: String): Type? {
        val packagePrefix = if (file.packageName != null) file.packageName + "." else ""
        return typesByCanonicalName[packagePrefix + name]
    }

    sealed class Type {
        data class Record(val record: Parser.Node.Record) : Type()
        data class Enumeration(val enumeration: Parser.Node.Enumeration) : Type()
    }
}

fun buildTypeTable(files: List<Parser.Ast>): TypeTable {
    val types = HashMap<String, TypeTable.Type>()
    for (file in files) {
        val packagePrefix = if (file.packageName != null) file.packageName + "." else ""

        for (record in file.records) {
            types[packagePrefix + record.name] = TypeTable.Type.Record(record)
        }

        for (enum in file.enumerations) {
            types[packagePrefix + enum.name] = TypeTable.Type.Enumeration(enum)
        }
    }

    return TypeTable(types)
}


// =====================================================================================================================
// Code generation
// =====================================================================================================================

class IndentedStringBuilder {
    private val base = StringBuilder()

    private var atStartOfLine = true
    private var currentIndent: String = ""

    fun indent(block: () -> Unit) {
        addIndentation()
        block()
        removeIndentation()
    }

    fun addIndentation() {
        currentIndent += "    "
    }

    fun removeIndentation() {
        currentIndent = currentIndent.removeSuffix("    ")
    }

    fun append(data: Any?) {
        val stringified = data.toString()
        if (stringified.contains("\n")) {
            val split = stringified.split("\n")
            for ((index, s) in split.withIndex()) {
                append(s)
                if (index != split.lastIndex) appendLine()
            }
        } else {
            if (atStartOfLine) base.append(currentIndent)
            base.append(data)
            atStartOfLine = false
        }
    }

    fun appendLine(text: String) {
        append(text)
        appendLine()
    }

    fun appendLine() {
        base.appendLine()
        atStartOfLine = true
    }

    fun reset() {
        base.clear()
        atStartOfLine = true
        currentIndent = ""
    }

    override fun toString() = base.toString()
}
enum class F(val encoded: Int) {
    Hund(2),

    ;companion object {
        fun fromEncoded(encoded: Int): F {
            return values().find { it.encoded == encoded } ?: error("bad")
        }
    }
}

data class KotlinConversion(
    val ktType: String,
    val size: Int,
    val getter: String,
    val setter: String,
    val encoder: String?,
    val decoder: String?,
    val jsonDecoder: String?,
)

private val kotlinConversionTable: Map<String, KotlinConversion> = buildMap {
    put("Byte", KotlinConversion("Byte", 1, "get", "put", null, null, ".toByte()"))
    put("UByte", KotlinConversion("UByte", 1, "get", "put", ".toUByte()", ".toByte()", ".toUByte()"))

    put("Short", KotlinConversion("Short", 2, "getShort", "putShort", null, null, ".toShort()"))
    put("UShort", KotlinConversion("UShort", 2, "getShort", "putShort", ".toUShort()", ".toShort()", ".toUShort()"))

    put("Int", KotlinConversion("Int", 4, "getInt", "putInt", null, null, ".toInt()"))
    put("UInt", KotlinConversion("UInt", 4, "getInt", "putInt", ".toUInt()", ".toInt()", ".toUInt()"))

    put("Long", KotlinConversion("Long", 8, "getLong", "putLong", null, null, ".toLong()"))
    put("ULong", KotlinConversion("ULong", 8, "getLong", "putLong", ".toULong()", ".toLong()", ".toULong()"))

    put("Boolean", KotlinConversion("Boolean", 1, "get", "put", " == 1.toByte()", ".let { if (it) 1 else 0 }", " == \"true\""))
}

fun lookupPropertyConversion(ast: Parser.Ast, types: TypeTable, property: Parser.Node.Property): KotlinConversion? {
    val type = types.lookupType(ast, property.type)
    return if (type is TypeTable.Type.Enumeration) {
        KotlinConversion(
            property.type,
            4,
            "getInt",
            "putInt",
            ".let { ${property.type}.fromEncoded(it) }",
            ".encoded",
            ".let { ${property.type}.valueOf(it) }"
        )
    } else {
        kotlinConversionTable[property.type]
    }
}

fun generateKotlinCode(ast: Parser.Ast, types: TypeTable): String {
    val toBeInsertedAfterRootRecord = IndentedStringBuilder()

    fun IndentedStringBuilder.visitEnum(enum: Parser.Node.Enumeration) {
        appendLine("enum class ${enum.name}(val encoded: Int) {")
        addIndentation()

        for ((entry, encoded) in enum.entries) {
            appendLine("$entry($encoded),")
        }


        appendLine(";companion object {")
        indent {
            appendLine("fun fromEncoded(encoded: Int): ${enum.name} {")
            indent {
                appendLine("return values().find { it.encoded == encoded } ?: error(\"Unknown enum encoding: ${"$"}encoded\")")
            }
            appendLine("}")
        }
        appendLine("}")

        removeIndentation()
        appendLine("}")
    }

    fun IndentedStringBuilder.visitRecord(record: Parser.Node.Record) {
        if (record.isTagged) {
            appendLine("sealed interface ${record.name} : BinaryType {")
            addIndentation()

            for (prop in record.properties) {
                val conversion = lookupPropertyConversion(ast, types, prop)
                if (conversion == null) {
                    if (prop.type == "String") {
                        append("abstract var _${prop.name}: Text")
                        if (prop.optional) append("?")
                        appendLine()

                        appendLine("abstract val ${prop.name}: String")
                    } else {
                        append("abstract var ${prop.name}: ${prop.type}")
                        if (prop.optional) append("?")
                        appendLine()
                    }
                } else {
                    append("abstract var ${prop.name}: ${conversion.ktType}")
                    if (prop.optional) append("?")
                    appendLine()
                }
            }

            for (child in record.nested) {
                visitRecord(child)
            }

            appendLine("companion object {")
            indent {
                appendLine("fun interpret(ptr: BufferAndOffset): ${record.name} {")
                indent {
                    appendLine("return when (val tag = ptr.data.get(ptr.offset).toInt()) {")
                    indent {
                        val ordinalsSeen = HashSet<Int>()

                        for (child in record.nested) {
                            if (child.implements != record.name) continue

                            val annotation = child.annotations.find { it.name == "Tag" }!!
                            val ordinalValueTok = annotation.attributes.find { it.first == "ordinal" }?.second as Token.Integer

                            if (ordinalValueTok.integer in ordinalsSeen) {
                                reportError(child.location, "Duplicate ordinal detected. " +
                                        "All ordinal values must be unique within a single tagged record!")
                            }

                            ordinalsSeen.add(ordinalValueTok.integer)

                            appendLine("${ordinalValueTok.integer} -> ${child.name}(ptr)")
                        }
                        appendLine("else -> error(\"Invalid ${record.name} representation stored: ${"$"}tag\")")
                    }
                    appendLine("}")
                }
                appendLine("}")

                appendLine()
                appendLine("fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ${record.name} {")
                indent {
                    appendLine("if (json !is JsonObject) error(\"${record.name} must be decoded from an object\")")
                    appendLine("val type = json[\"type\"]")
                    appendLine("if (type !is JsonPrimitive) error(\"Missing type tag\")")
                    appendLine("return when (type.content) {")
                    indent {
                        val namesSeen = HashSet<String>()

                        for (child in record.nested) {
                            if (child.implements != record.name) continue

                            val annotation = child.annotations.find { it.name == "Tag" }!!
                            val nameTokValue = annotation.attributes.find { it.first == "text" }?.second as Token.Text

                            if (nameTokValue.text in namesSeen) {
                                reportError(child.location, "Duplicate text detected. " +
                                        "All text values must be unique within a single tagged record!")
                            }

                            namesSeen.add(nameTokValue.text)

                            appendLine("\"${nameTokValue.text}\" -> ${child.name}.decodeFromJson(allocator, json)")
                        }
                        appendLine("else -> error(\"Unknown type: ${"$"}{type.content}\")")
                    }
                    appendLine("}")
                }
                appendLine("}")
            }
            appendLine("}")

            removeIndentation()
            appendLine("}")
        } else {
            appendLine("@JvmInline")
            append("value class ${record.name}(override val buffer: BufferAndOffset) : BinaryType")
            if (record.implements != null) append(", ${record.implements}")
            appendLine(" {")
            addIndentation()

            var offset = 0
            if (record.implements != null) {
                val annotation = record.annotations.find { it.name == "Tag" } ?:
                    reportError(record.location, "Mandatory @Tag(text = \"name\", ordinal = 42) annotation is missing")

                val ordinal = annotation.attributes.find { it.first == "ordinal" }
                val ordinalValueTok = ordinal?.second
                if (ordinalValueTok !is Token.Integer) {
                    reportError(annotation.location, "Missing or invalid ordinal attribute in the @Tag " +
                            "annotation. It must exist with an integer value!")
                }

                if (ordinalValueTok.integer !in 0..127) {
                    reportError(ordinalValueTok.location, "Ordinal value must be an integer between 0 and 127 (inclusive)")
                }

                appendLine("init {")
                indent {
                    appendLine("buffer.data.put(0 + buffer.offset, ${ordinalValueTok.integer})")
                }
                appendLine("}")
                offset++
            }

            for (prop in record.properties) {
                val varPrefix = if (record.implements == null) {
                    ""
                } else {
                    val parentType = types.lookupType(ast, record.implements)
                    if (parentType !is TypeTable.Type.Record) {
                        reportError(record.location, "Unknown type: ${record.implements}")
                    }

                    if (parentType.record.properties.any { it.name == prop.name }) {
                        "override "
                    } else {
                        ""
                    }
                }

                if (offset != 0) appendLine()

                val conversion = lookupPropertyConversion(ast, types, prop)
                if (conversion == null || prop.optional) {
                    // Store through pointer

                    if (prop.type == "String") {
                        append(varPrefix + "var _${prop.name}: Text")
                        if (prop.optional) append("?")
                        appendLine()
                        addIndentation()

                        if (!prop.optional) {
                            appendLine("inline get() = Text(buffer.copy(offset = buffer.data.getInt($offset + buffer.offset)))")
                            appendLine("inline set(value) { buffer.data.putInt($offset + buffer.offset, value.buffer.offset) }")
                        } else {
                            appendLine("inline get() {")
                            addIndentation()
                            appendLine("val offset = buffer.data.getInt($offset + buffer.offset)")
                            appendLine("return if (offset == 0) null else Text(buffer.copy(offset = offset))")
                            removeIndentation()
                            appendLine("}")

                            appendLine("inline set(value) { buffer.data.putInt($offset + buffer.offset, value?.buffer?.offset ?: 0) }")
                        }

                        removeIndentation()

                        append(varPrefix + "val ${prop.name}: String")
                        if (prop.optional) append("?")
                        appendLine()
                        addIndentation()
                        if (prop.optional) {
                            appendLine("inline get() = _${prop.name}?.decode()")
                        } else {
                            appendLine("inline get() = _${prop.name}.decode()")
                        }
                        removeIndentation()
                    } else {
                        if (conversion != null) {
                            val (ktType, size, getter, setter, encoder, decoder) = conversion

                            appendLine(varPrefix + "var ${prop.name}: ${ktType}?")
                            addIndentation()

                            appendLine("inline get() {")
                            indent {
                                appendLine("val offset = buffer.data.getInt($offset + buffer.offset)")
                                appendLine("return if (offset == 0) {")
                                indent { appendLine("null") }
                                appendLine("} else {")
                                indent {
                                    append("buffer.data.$getter(offset)")
                                    if (encoder != null) append(encoder)
                                    appendLine()
                                }
                                appendLine("}")
                            }
                            appendLine("}")

                            appendLine("inline set(value) {")
                            indent {
                                appendLine("if (value == null) {")
                                indent { appendLine("buffer.data.putInt($offset + buffer.offset, 0)") }
                                appendLine("} else {")
                                indent {
                                    appendLine("val storage = buffer.allocator.allocateDynamic($size)")
                                    appendLine("buffer.data.putInt($offset + buffer.offset, storage.offset)")
                                    append("buffer.data.$setter(storage.offset, value")
                                    if (decoder != null) append(decoder)
                                    appendLine(")")
                                }
                                appendLine("}")
                            }
                            appendLine("}")

                            removeIndentation()
                        } else {
                            append(varPrefix + "var ${prop.name}: ${prop.type}")
                            if (prop.optional) append("?")
                            appendLine()
                            addIndentation()

                            appendLine("inline get() {")
                            indent {
                                appendLine("val offset = buffer.data.getInt($offset + buffer.offset)")
                                appendLine("return (if (offset == 0) {")
                                indent { appendLine("null") }
                                appendLine("} else {")
                                indent {
                                    append(prop.type)
                                    val type = types.lookupType(ast, prop.type)
                                    if (type is TypeTable.Type.Record && type.record.isTagged) {
                                        append(".interpret")
                                    }
                                    appendLine("(buffer.copy(offset = offset))")
                                }
                                append("})")
                                if (!prop.optional) append("!!")
                                appendLine()
                            }
                            appendLine("}")

                            appendLine("inline set(value) {")
                            indent {
                                appendLine("buffer.data.putInt($offset + buffer.offset, value?.buffer?.offset ?: 0)")
                            }
                            appendLine("}")

                            removeIndentation()
                        }
                    }

                    offset += 4
                } else {
                    val (ktType, size, getter, setter, encoder, decoder) = conversion
                    appendLine(varPrefix + "var ${prop.name}: $ktType")
                    addIndentation()

                    append("inline get() = buffer.data.$getter($offset + buffer.offset)")
                    if (encoder != null) append(encoder)
                    appendLine()

                    append("inline set (value) { ")
                    append("buffer.data.${setter}($offset + buffer.offset, value")
                    if (decoder != null) append(decoder)
                    append(") ")
                    appendLine("}")

                    removeIndentation()

                    offset += size
                }
            }

            appendLine()
            appendLine("override fun encodeToJson(): JsonElement = JsonObject(mapOf(")
            indent {
                if (record.implements != null) {
                    val annotation = record.annotations.find { it.name == "Tag" } ?:
                    reportError(record.location, "Mandatory @Tag(text = \"name\", ordinal = 42) annotation is missing")

                    val name = annotation.attributes.find { it.first == "text" }
                    val nameValueTok = name?.second
                    if (nameValueTok !is Token.Text) {
                        reportError(annotation.location, "Missing or invalid text attribute in the @Tag " +
                                "annotation. It must exist with an string value!")
                    }

                    appendLine("\"type\" to JsonPrimitive(\"${nameValueTok.text}\"),")
                }

                for (prop in record.properties) {
                    append("\"${prop.name}\" to (")
                    append(prop.name)
                    if (prop.optional) append("?")
                    append(".let { ")
                    val converter = lookupPropertyConversion(ast, types, prop)
                    if (converter != null || prop.type == "String") {
                        val type = types.lookupType(ast, prop.type)
                        if (type is TypeTable.Type.Enumeration) {
                            append("JsonPrimitive(it.name)")
                        } else {
                            append("JsonPrimitive(it)")
                        }
                    } else {
                        append("it.encodeToJson()")
                    }
                    append(" }")
                    if (prop.optional) append(" ?: JsonNull")
                    appendLine("),")
                }
            }
            appendLine("))")

            appendLine()
            appendLine("companion object : BinaryTypeCompanion<${record.name}> {")
            addIndentation()
            appendLine("override val size = $offset")
            appendLine("override fun create(buffer: BufferAndOffset) = ${record.name}(buffer)")

            appendLine("override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ${record.name} {")
            indent {
                appendLine("if (json !is JsonObject) error(\"${record.name} must be decoded from an object\")")
                for (prop in record.properties) {
                    appendLine("val ${prop.name} = run {")
                    indent {
                        appendLine("val element = json[\"${prop.name}\"]")
                        appendLine("if (element == null || element == JsonNull) {")
                        indent { appendLine("null") }
                        appendLine("} else {")
                        indent {
                            val converter = lookupPropertyConversion(ast, types, prop)
                            if (converter != null) {
                                appendLine("if (element !is JsonPrimitive) error(\"Expected '${prop.name}' to be a primitive\")")
                                appendLine("element.content${converter.jsonDecoder}")
                            } else if (prop.type == "String") {
                                appendLine("if (element !is JsonPrimitive) error(\"Expected '${prop.name}' to be a primitive\")")
                                appendLine("element.content")
                            } else {
                                appendLine("${prop.type}.decodeFromJson(allocator, element)")
                            }
                        }
                        appendLine("}")
                    }
                    append("}")
                    if (!prop.optional) {
                        append(" ?: error(\"Missing required property: ${prop.name} in ${record.name}\")")
                    }
                    appendLine()
                }
                val recordConstructor = if (record.implements != null) {
                    record.implements + record.name
                } else {
                    record.name
                }
                appendLine("return allocator.${recordConstructor}(")
                indent {
                    for (prop in record.properties) {
                        appendLine("${prop.name} = ${prop.name},")
                    }
                }
                appendLine(")")
            }
            appendLine("}")

            removeIndentation()
            appendLine("}")

            for (child in record.nested) {
                visitRecord(child)
            }

            removeIndentation()
            appendLine("}")

            appendLine()

            with(toBeInsertedAfterRootRecord) {
                appendLine("fun BinaryAllocator.${record.implements ?: ""}${record.name}(")
                val recordReference = buildString {
                    if (record.implements != null) {
                        append(record.implements)
                        append(".")
                    }
                    append(record.name)
                }

                indent {
                    for (prop in record.properties) {
                        append(prop.name)
                        append(": ")

                        val conversion = lookupPropertyConversion(ast, types, prop)
                        if (conversion != null) {
                            append(conversion.ktType)
                        } else {
                            append(prop.type)
                        }

                        if (prop.optional) append("? = null")
                        appendLine(",")
                    }
                }
                appendLine("): ${recordReference} {")
                indent {
                    appendLine("val result = this.allocate(${recordReference})")
                    for (prop in record.properties) {
                        if (prop.type == "String") {
                            append("result._${prop.name} = ${prop.name}")
                            if (prop.optional) append("?")
                            appendLine(".let { allocateText(it) }")
                        } else {
                            appendLine("result.${prop.name} = ${prop.name}")
                        }
                    }
                    appendLine("return result")
                }
                appendLine("}")
            }
        }
    }

    return IndentedStringBuilder().apply {
        repeat(3) { appendLine("// GENERATED CODE - DO NOT MODIFY") }
        appendLine()

        if (ast.packageName != null) {
            appendLine("package ${ast.packageName}")
            appendLine()
        }

        for (enum in ast.enumerations) {
            visitEnum(enum)
            appendLine()
        }

        for (record in ast.records) {
            visitRecord(record)
            appendLine()
            if (toBeInsertedAfterRootRecord.toString().isNotEmpty()) {
                appendLine(toBeInsertedAfterRootRecord.toString())
                toBeInsertedAfterRootRecord.reset()
            }
        }
    }.toString()
}
