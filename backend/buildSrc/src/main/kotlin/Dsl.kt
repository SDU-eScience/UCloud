// =====================================================================================================================
// Parsing
// =====================================================================================================================

data class Location(
    var fileIdentifier: String,
    var column: Int,
    var line: Int
) {
    override fun toString(): String {
        return "$fileIdentifier at $line:$column"
    }
}

class Cursor(
    val fileIdentifier: String,
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
            DICTIONARY,
            ARRAY,
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

            "/*", "*/" -> {
                reportError(cursor.location, "C-style multi-line comments are not supported. Use '//' instead.")
            }

            "[]" -> return Token.Symbol(location, Token.Symbol.Type.ARRAY).also { cursor.consume(2) }
            "{}" -> return Token.Symbol(location, Token.Symbol.Type.DICTIONARY).also { cursor.consume(2) }
        }

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
            val entries: List<EnumEntry>,
        ) : Node() {
            data class EnumEntry(val name: String, val ordinal: Int, val annotations: List<Annotation>)

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
            val typeModifier: TypeModifier,
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

        enum class TypeModifier {
            NONE,
            ARRAY,
            DICTIONARY,
//            ARRAY_OF_DICTIONARIES,
//            DICTIONARY_OF_ARRAYS,
        }
    }

    data class Ast(
        val fileIdentifier: String,
        val packageName: String?,
        val imports: List<String>,
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
        val imports = ArrayList<String>()

        val initial = Lexer.consume(cursor)
        var next: Token? = initial
        if (initial is Token.Identifier && initial.identifier == "package") {
            cursor.consumeWhitespace()
            packageName = cursor.consumeRestOfLine()
            next = Lexer.consume(cursor)
        }

        var docComment: String? = null
        var annotations = ArrayList<Node.Annotation>()
        val records = ArrayList<Node.Record>()
        val enumerations = ArrayList<Node.Enumeration>()
        while (true) {
            when {
                next is Token.Identifier && next.identifier == "import" -> {
                    imports.add(cursor.consumeRestOfLine().trim())
                }

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

        return Ast(cursor.fileIdentifier, packageName, imports, records, enumerations)
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
                        reportError(
                            nameTok.location,
                            "Unexpected symbol. Did you mean to end the annotation with a ')'?"
                        )
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
                    reportError(
                        valueTok?.location,
                        "Expected either a string or an integer. For example: @Stable(version = 1)"
                    )
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
        val startOfProperty = initialTok.location

        var isOptional = false
        val name = initialTok.identifier
        var type: String = ""
        var typeModifier = Node.TypeModifier.NONE

        run {
            val next = Lexer.consume(cursor)
            if (next !is Token.Symbol || next.type != Token.Symbol.Type.COLON) {
                reportError(next?.location, "Expected colon to start the type. For example: `path: String,`")
            }
        }

        run {
            val next = Lexer.consume(cursor)
            if (next !is Token.Identifier) {
                reportError(next?.location, "Expected a type for the property. For example: `path: String,`")
            }

            type = next.identifier
        }

        run {
            var nextTok = Lexer.consume(cursor)
            run {
                val next = nextTok
                if (next is Token.Symbol && next.type == Token.Symbol.Type.QUESTION_MARK) {
                    isOptional = true
                    nextTok = Lexer.consume(cursor)
                }
            }

            run {
                val next = nextTok

                var first: Token.Symbol.Type? = null
                var second: Token.Symbol.Type? = null

                if (next is Token.Symbol) {
                    when (next.type) {
                        Token.Symbol.Type.DICTIONARY,
                        Token.Symbol.Type.ARRAY -> first = next.type

                        else -> {}
                    }

                    if (first != null) nextTok = Lexer.consume(cursor)
                }

                val next2 = nextTok
                if (first != null && next2 is Token.Symbol) {
                    when (next2.type) {
                        Token.Symbol.Type.DICTIONARY,
                        Token.Symbol.Type.ARRAY -> second = next2.type

                        else -> {}
                    }

                    if (second != null) nextTok = Lexer.consume(cursor)
                }

                when {
                    first == Token.Symbol.Type.ARRAY && second == null -> typeModifier = Node.TypeModifier.ARRAY
                    first == Token.Symbol.Type.DICTIONARY && second == null -> typeModifier =
                        Node.TypeModifier.DICTIONARY

//                    first == Token.Symbol.Type.ARRAY && second == Token.Symbol.Type.DICTIONARY -> typeModifier =
//                        Node.TypeModifier.ARRAY_OF_DICTIONARIES
//
//                    first == Token.Symbol.Type.DICTIONARY && second == Token.Symbol.Type.ARRAY -> typeModifier =
//                        Node.TypeModifier.DICTIONARY_OF_ARRAYS

                    else -> {
                        if (first != null) {
                            reportError(
                                cursor.location,
                                "Invalid type modifier combination. Only the following are supported: '[]', '{}', '[]{}', '{}[]"
                            )
                        }
                    }
                }
            }

            run {
                val next = nextTok
                if (next !is Token.Symbol || next.type != Token.Symbol.Type.COMMA) {
                    reportError(next?.location, "Expected a comma to end the property. For example: `path: String,`")
                }
            }
        }

        return Node.Property(startOfProperty!!, annotations, name, type, typeModifier, isOptional)
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

        var entryAnnotations = ArrayList<Node.Annotation>()
        val entries = ArrayList<Node.Enumeration.EnumEntry>()
        while (true) {
            val initial = Lexer.consume(cursor)
            if (initial is Token.Symbol && initial.type == Token.Symbol.Type.CURLY_END) break

            if (initial is Token.Symbol && initial.type == Token.Symbol.Type.AT_SYMBOL) {
                entryAnnotations.add(parseAnnotation())
                continue
            }

            if (initial !is Token.Identifier) {
                reportError(initial?.location, "Expected identifier to start enum. Example: `FOO = 1,`")
            }

            val equalsTok = Lexer.consume(cursor)
            if (equalsTok !is Token.Symbol || equalsTok.type != Token.Symbol.Type.EQUALS) {
                reportError(equalsTok?.location, "Expected '=' to begin the ordinal of an enum. Example: `FOO = 1,`")
            }

            val ordinalTok = Lexer.consume(cursor)
            if (ordinalTok !is Token.Integer) {
                reportError(
                    ordinalTok?.location,
                    "Expected an integer to define the enum's ordinal. Example: `FOO = 1,`"
                )
            }

            val commaTok = Lexer.consume(cursor)
            if (commaTok !is Token.Symbol || commaTok.type != Token.Symbol.Type.COMMA) {
                reportError(
                    commaTok?.location,
                    "Expected a comma to end the enum. This is mandatory even for the last element!"
                )
            }

            entries.add(Node.Enumeration.EnumEntry(initial.identifier, ordinalTok.integer, entryAnnotations))
            entryAnnotations = ArrayList()
        }

        return Node.Enumeration(nameTok.location, nameTok.identifier, annotations, entries)
    }
}

fun reportError(location: Location?, message: String): Nothing =
    error("Error in $location $message")

// =====================================================================================================================
// Type table
// =====================================================================================================================
data class TypeTable(
    val typesByCanonicalName: Map<String, Type>,
) {
    fun lookupType(file: Parser.Ast, name: String): Type? {
        val packageNames = ArrayList<String?>()
        packageNames.add(file.packageName)
        packageNames.addAll(file.imports)

        for (pack in packageNames) {
            val packagePrefix = if (pack != null) "$pack." else ""
            val result = typesByCanonicalName[packagePrefix + name]
            if (result != null) return result
        }
        return null
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

data class KotlinConversion(
    val ktType: String,
    val size: Int,
    val getter: String,
    val setter: String,
    val encoder: String?,
    val decoder: String?,
    val jsonDecoder: String?,
)

private val kotlinConversionTable: Map<String, KotlinConversion> = HashMap<String, KotlinConversion>().apply {
    put("Byte", KotlinConversion("Byte", 1, "get", "put", null, null, ".toByte()"))
    put("UByte", KotlinConversion("UByte", 1, "get", "put", ".toUByte()", ".toByte()", ".toUByte()"))

    put("Short", KotlinConversion("Short", 2, "getShort", "putShort", null, null, ".toShort()"))
    put("UShort", KotlinConversion("UShort", 2, "getShort", "putShort", ".toUShort()", ".toShort()", ".toUShort()"))

    put("Int", KotlinConversion("Int", 4, "getInt", "putInt", null, null, ".toInt()"))
    put("UInt", KotlinConversion("UInt", 4, "getInt", "putInt", ".toUInt()", ".toInt()", ".toUInt()"))

    put("Long", KotlinConversion("Long", 8, "getLong", "putLong", null, null, ".toLong()"))
    put("ULong", KotlinConversion("ULong", 8, "getLong", "putLong", ".toULong()", ".toLong()", ".toULong()"))

    put(
        "Boolean",
        KotlinConversion("Boolean", 1, "get", "put", " == 1.toByte()", ".let { if (it) 1 else 0 }", " == \"true\"")
    )
}

fun lookupPropertyConversion(ast: Parser.Ast, types: TypeTable, property: Parser.Node.Property): KotlinConversion? {
    val type = types.lookupType(ast, property.type)
    return if (type is TypeTable.Type.Enumeration) {
        KotlinConversion(
            property.type,
            2,
            "getShort",
            "putShort",
            ".let { ${property.type}.fromEncoded(it.toInt()) }",
            ".encoded.toShort()",
            ".let { ${property.type}.fromSerialName(it) }"
        )
    } else {
        kotlinConversionTable[property.type]
    }
}

fun generateKotlinCode(ast: Parser.Ast, types: TypeTable): String {
    val toBeInsertedAfterRootRecord = IndentedStringBuilder()

    fun IndentedStringBuilder.visitEnum(enum: Parser.Node.Enumeration) {
        appendLine("enum class ${enum.name}(val encoded: Int, val serialName: String) {")
        addIndentation()

        for ((entry, encoded, annotations) in enum.entries) {
            val jsonNameAnnotation = annotations.find { it.name == "Json" }
                ?.attributes?.find { it.first == "name" }?.second

            val serialName = if (jsonNameAnnotation is Token.Text) {
                jsonNameAnnotation.text
            } else {
                entry
            }

            appendLine("$entry($encoded, \"$serialName\"),")
        }


        appendLine(";companion object {")
        indent {
            appendLine("fun fromEncoded(encoded: Int): ${enum.name} {")
            indent {
                appendLine("return values().find { it.encoded == encoded } ?: error(\"Unknown enum encoding: ${"$"}encoded\")")
            }
            appendLine("}")

            appendLine()
            appendLine("fun fromSerialName(name: String): ${enum.name} {")
            indent {
                appendLine("return values().find { it.serialName == name } ?: error(\"Unknown enum encoding: ${"$"}name\")")
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
                when (prop.typeModifier) {
                    Parser.Node.TypeModifier.ARRAY,
                    Parser.Node.TypeModifier.DICTIONARY -> {
                        append("abstract var ${prop.name}: ${prop.type}")
                        if (prop.optional) append("?")
                        appendLine()
                    }

                    Parser.Node.TypeModifier.NONE -> {
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
                }
            }

            for (child in record.nested) {
                visitRecord(child)
            }

            appendLine("companion object : BinaryTypeCompanion<${record.name}> {")
            indent {
                appendLine("override val size = 0")
                appendLine("override fun create(buffer: BufferAndOffset) = interpret(buffer)")
                appendLine()

                appendLine("fun interpret(ptr: BufferAndOffset): ${record.name} {")
                indent {
                    appendLine("return when (val tag = ptr.data.get(ptr.offset).toInt()) {")
                    indent {
                        val ordinalsSeen = HashSet<Int>()

                        for (child in record.nested) {
                            if (child.implements != record.name) continue

                            val annotation = child.annotations.find { it.name == "Tag" }!!
                            val ordinalValueTok =
                                annotation.attributes.find { it.first == "ordinal" }?.second as Token.Integer

                            if (ordinalValueTok.integer in ordinalsSeen) {
                                reportError(
                                    child.location, "Duplicate ordinal detected. " +
                                            "All ordinal values must be unique within a single tagged record!"
                                )
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
                appendLine("override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): ${record.name} {")
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
                                reportError(
                                    child.location, "Duplicate text detected. " +
                                            "All text values must be unique within a single tagged record!"
                                )
                            }

                            namesSeen.add(nameTokValue.text)

                            append("\"${nameTokValue.text}\" -> ${child.name}.decodeFromJson(allocator, json)")
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
                val annotation = record.annotations.find { it.name == "Tag" } ?: reportError(
                    record.location,
                    "Mandatory @Tag(text = \"name\", ordinal = 42) annotation is missing"
                )

                val ordinal = annotation.attributes.find { it.first == "ordinal" }
                val ordinalValueTok = ordinal?.second
                if (ordinalValueTok !is Token.Integer) {
                    reportError(
                        annotation.location, "Missing or invalid ordinal attribute in the @Tag " +
                                "annotation. It must exist with an integer value!"
                    )
                }

                if (ordinalValueTok.integer !in 0..127) {
                    reportError(
                        ordinalValueTok.location,
                        "Ordinal value must be an integer between 0 and 127 (inclusive)"
                    )
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

                when (prop.typeModifier) {
                    Parser.Node.TypeModifier.ARRAY -> {
                        val conversion = lookupPropertyConversion(ast, types, prop)
                        if (conversion != null) {
                            reportError(prop.location, "Arrays can only contain records at the moment. Please wrap the primitive type in a record.")
                        }

                        val type = if (prop.type == "String") "Text" else prop.type
                        append(varPrefix + "var ${prop.name}: BinaryTypeList<${type}>")
                        if (prop.optional) append("?")
                        appendLine()
                        indent {
                            appendLine("inline get() {")
                            indent {
                                appendLine("val offset = buffer.data.getInt($offset + buffer.offset)")
                                append("return (if (offset == 0) null else BinaryTypeList(${type}, buffer.copy(offset = offset)))")
                                if (!prop.optional) append(" ?: error(\"Missing property ${prop.name}\")")
                                appendLine()
                            }
                            appendLine("}")

                            appendLine("inline set(value) { buffer.data.putInt($offset + buffer.offset, value.buffer.offset) }")
                        }

                        offset += 4
                    }

                    Parser.Node.TypeModifier.DICTIONARY -> {
                        val conversion = lookupPropertyConversion(ast, types, prop)
                        if (conversion != null) {
                            reportError(prop.location, "Dictionaries can only contain records at the moment. Please wrap the primitive type in a record.")
                        }

                        val type = if (prop.type == "String") "Text" else prop.type
                        append(varPrefix + "var ${prop.name}: BinaryTypeDictionary<${type}>")
                        if (prop.optional) append("?")
                        appendLine()
                        indent {
                            appendLine("inline get() {")
                            indent {
                                appendLine("val offset = buffer.data.getInt($offset + buffer.offset)")
                                append("return (if (offset == 0) null else BinaryTypeDictionary(${type}, buffer.copy(offset = offset)))")
                                if (!prop.optional) append(" ?: error(\"Missing property ${prop.name}\")")
                                appendLine()
                            }
                            appendLine("}")

                            appendLine("inline set(value) { buffer.data.putInt($offset + buffer.offset, value.buffer.offset) }")
                        }

                        offset += 4
                    }

                    Parser.Node.TypeModifier.NONE -> {
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
                }
            }

            appendLine()
            appendLine("override fun encodeToJson(): JsonElement = JsonObject(mapOf(")
            indent {
                if (record.implements != null) {
                    val annotation = record.annotations.find { it.name == "Tag" } ?: reportError(
                        record.location,
                        "Mandatory @Tag(text = \"name\", ordinal = 42) annotation is missing"
                    )

                    val name = annotation.attributes.find { it.first == "text" }
                    val nameValueTok = name?.second
                    if (nameValueTok !is Token.Text) {
                        reportError(
                            annotation.location, "Missing or invalid text attribute in the @Tag " +
                                    "annotation. It must exist with an string value!"
                        )
                    }

                    appendLine("\"type\" to JsonPrimitive(\"${nameValueTok.text}\"),")
                }

                for (prop in record.properties) {
                    append("\"${prop.name}\" to (")
                    append(prop.name)
                    if (prop.optional) append("?")
                    append(".let { ")

                    when (prop.typeModifier) {
                        Parser.Node.TypeModifier.ARRAY,
                        Parser.Node.TypeModifier.DICTIONARY -> {
                            append("it.encodeToJson()")
                        }

                        Parser.Node.TypeModifier.NONE -> {
                            val converter = lookupPropertyConversion(ast, types, prop)
                            if (converter != null || prop.type == "String") {
                                val type = types.lookupType(ast, prop.type)
                                if (type is TypeTable.Type.Enumeration) {
                                    append("JsonPrimitive(it.serialName)")
                                } else {
                                    append("JsonPrimitive(it)")
                                }
                            } else {
                                append("it.encodeToJson()")
                            }
                        }
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
            appendLine("private val mySerializer = BinaryTypeSerializer(this)")
            appendLine("fun serializer() = mySerializer")
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
                            when (prop.typeModifier) {
                                Parser.Node.TypeModifier.ARRAY -> {
                                    val type = if (prop.type == "String") "Text" else prop.type
                                    appendLine("BinaryTypeList.decodeFromJson(${type}, allocator, element)")
                                }

                                Parser.Node.TypeModifier.DICTIONARY -> {
                                    val type = if (prop.type == "String") "Text" else prop.type
                                    appendLine("BinaryTypeDictionary.decodeFromJson(${type}, allocator, element)")
                                }

                                Parser.Node.TypeModifier.NONE -> {
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

                        when (prop.typeModifier) {
                            Parser.Node.TypeModifier.ARRAY -> {
                                val type = if (prop.type == "String") "Text" else prop.type
                                append("BinaryTypeList<${type}>")
                            }

                            Parser.Node.TypeModifier.DICTIONARY -> {
                                val type = if (prop.type == "String") "Text" else prop.type
                                append("BinaryTypeDictionary<${type}>")
                            }

                            Parser.Node.TypeModifier.NONE -> {
                                val conversion = lookupPropertyConversion(ast, types, prop)
                                if (conversion != null) {
                                    append(conversion.ktType)
                                } else {
                                    append(prop.type)
                                }
                            }
                        }


                        if (prop.optional) append("? = null")
                        appendLine(",")
                    }
                }
                appendLine("): ${recordReference} {")
                indent {
                    appendLine("val result = this.allocate(${recordReference})")
                    for (prop in record.properties) {
                        when (prop.typeModifier) {
                            Parser.Node.TypeModifier.DICTIONARY,
                            Parser.Node.TypeModifier.ARRAY -> {
                                appendLine("result.${prop.name} = ${prop.name}")
                            }

                            Parser.Node.TypeModifier.NONE -> {
                                if (prop.type == "String") {
                                    append("result._${prop.name} = ${prop.name}")
                                    if (prop.optional) append("?")
                                    appendLine(".let { allocateText(it) }")
                                } else {
                                    appendLine("result.${prop.name} = ${prop.name}")
                                }
                            }
                        }
                    }
                    appendLine("return result")
                }
                appendLine("}")
            }
        }
    }

    return IndentedStringBuilder().apply {
        repeat(3) { appendLine("// GENERATED CODE - DO NOT MODIFY - See ${ast.fileIdentifier.substringAfterLast("/")}") }
        appendLine()

        if (ast.packageName != null) {
            appendLine("package dk.sdu.cloud.${ast.packageName.trim()}.api")
            appendLine()
        }

        appendLine(
            """
            import kotlinx.serialization.json.JsonElement
            import kotlinx.serialization.json.JsonNull
            import kotlinx.serialization.json.JsonObject
            import kotlinx.serialization.json.JsonPrimitive
            import dk.sdu.cloud.messages.*
            
        """.trimIndent()
        )

        for (import in ast.imports) {
            appendLine("import dk.sdu.cloud.$import.api.*")
        }
        if (ast.imports.isNotEmpty()) appendLine()

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

fun generateTypeScriptCode(ast: Parser.Ast, types: TypeTable): String {
    val toBeInsertedAfterRootRecord = IndentedStringBuilder()

    fun serialName(enum: Parser.Node.Enumeration.EnumEntry): String {
        val jsonNameAnnotation = enum.annotations.find { it.name == "Json" }
            ?.attributes?.find { it.first == "name" }?.second

        return if (jsonNameAnnotation is Token.Text) {
            jsonNameAnnotation.text
        } else {
            enum.name
        }
    }

    fun IndentedStringBuilder.visitEnum(enum: Parser.Node.Enumeration) {
        appendLine("export enum ${enum.name} {")
        indent {
            for (entry in enum.entries) {
                appendLine(entry.name + ",")
            }
        }
        appendLine("}")

        appendLine()
        appendLine("export const ${enum.name}Companion = {")
        indent {
            appendLine("name(element: ${enum.name}): string {")
            indent {
                appendLine("switch (element) {")
                indent {
                    for (entry in enum.entries) {
                        appendLine("case ${enum.name}.${entry.name}: return \"${entry.name}\";")
                    }
                }
                appendLine("}")
            }
            appendLine("},")

            appendLine()
            appendLine("serialName(element: ${enum.name}): string {")
            indent {
                appendLine("switch (element) {")
                indent {
                    for (entry in enum.entries) {
                        appendLine("case ${enum.name}.${entry.name}: return \"${serialName(entry)}\";")
                    }
                }
                appendLine("}")
            }
            appendLine("},")

            appendLine()
            appendLine("encoded(element: ${enum.name}): number {")
            indent {
                appendLine("switch (element) {")
                indent {
                    for (entry in enum.entries) {
                        appendLine("case ${enum.name}.${entry.name}: return ${entry.ordinal};")
                    }
                }
                appendLine("}")
            }
            appendLine("},")

            appendLine()
            appendLine("fromSerialName(name: string): ${enum.name} | null {")
            indent {
                appendLine("switch (name) {")
                indent {
                    for (entry in enum.entries) {
                        appendLine("case \"${serialName(entry)}\": return ${enum.name}.${entry.name};")
                    }
                    appendLine("default: return null;")
                }
                appendLine("}")
            }
            appendLine("},")

            appendLine()
            appendLine("fromEncoded(encoded: number): ${enum.name} | null {")
            indent {
                appendLine("switch (encoded) {")
                indent {
                    for (entry in enum.entries) {
                        appendLine("case ${entry.ordinal}: return ${enum.name}.${entry.name};")
                    }
                    appendLine("default: return null;")
                }
                appendLine("}")
            }
            appendLine("},")
        }
        appendLine("};")
    }

    fun primitiveType(type: String): String {
        return when (type) {
            "Byte", "Short", "Int", "Long" -> "number"
            "Boolean" -> "boolean"
            else -> error("??? $type ???")
        }
    }

    fun primitiveSize(type: String): Int {
        return when (type) {
            "Byte" -> 1
            "Short" -> 2
            "Int" -> 4
            "Long" -> 8
            "Boolean" -> 1
            else -> error("??? $type ???")
        }
    }

    fun IndentedStringBuilder.getOrSetPrimitive(
        isSetter: Boolean,
        type: String,
        offsetExpr: String,
        valueExpr: String?,
        bufferExpr: String,
    ): Int {
        val size = primitiveSize(type)

        val getterFn = "getInt${size * 8}"
        val setterFn = "setInt${size * 8}"
        val getterConversion = if (type == "Boolean") {
            " == 1"
        } else {
            ""
        }
        val setterConversion = if (type == "Boolean") {
            " ? 1 : 0"
        } else {
            ""
        }

        if (isSetter) {
            appendLine("$bufferExpr.$setterFn($offsetExpr, $valueExpr${setterConversion})")
        } else {
            appendLine("$bufferExpr.$getterFn($offsetExpr)$getterConversion")
        }

        return size
    }

    fun tsType(type: String): String {
        return when (type) {
            "Byte", "Short", "Int", "Long",
            "Boolean" -> {
                primitiveType(type)
            }

            "String" -> "UText"

            else -> {
                when (val lookup = types.lookupType(ast, type)) {
                    null -> type
                    is TypeTable.Type.Enumeration -> type
                    is TypeTable.Type.Record -> {
                        if (lookup.record.implements != null) {
                            lookup.record.implements + lookup.record.name
                        } else {
                            lookup.record.name
                        }
                    }
                }
            }
        }
    }

    fun tsTypeReference(property: Parser.Node.Property): String {
        when (property.typeModifier) {
            Parser.Node.TypeModifier.NONE -> {
                val baseType = tsType(property.type)
                return if (property.optional) {
                    "$baseType | null"
                } else {
                    baseType
                }
            }

            Parser.Node.TypeModifier.ARRAY -> {
                val baseType = tsType(property.type)
                val typeWithoutNull = "BinaryTypeList<$baseType>"
                return if (property.optional) {
                    "$typeWithoutNull | null"
                } else {
                    typeWithoutNull
                }
            }

            Parser.Node.TypeModifier.DICTIONARY -> {
                val baseType = tsType(property.type)
                val typeWithoutNull = "BinaryTypeDictionary<$baseType>"
                return if (property.optional) {
                    "$typeWithoutNull | null"
                } else {
                    typeWithoutNull
                }
            }
        }
    }

    fun tsTypeInConstructor(property: Parser.Node.Property): String {
        return if (property.type == "String" && property.typeModifier == Parser.Node.TypeModifier.NONE) {
            if (property.optional) "string | null"
            else "string"
        } else {
            tsTypeReference(property)
        }
    }

    fun isPrimitive(type: String): Boolean {
        return when (type) {
            "Byte", "Short", "Int", "Long",
            "Boolean" -> true

            else -> false
        }
    }

    fun shouldAccessThroughPointer(property: Parser.Node.Property): Boolean {
        when (property.typeModifier) {
            Parser.Node.TypeModifier.ARRAY -> return true
            Parser.Node.TypeModifier.DICTIONARY -> return true
            Parser.Node.TypeModifier.NONE -> {
                if (property.optional) return true
                if (isPrimitive(property.type)) return false
                val typeLookup = types.lookupType(ast, property.name)
                return typeLookup !is TypeTable.Type.Enumeration
            }
        }
    }

    fun IndentedStringBuilder.visitRecord(record: Parser.Node.Record) {
        if (record.isTagged) {
            appendLine("export type ${record.name} =")
            indent {
                var idx = 0
                for (nested in record.nested) {
                    val qualifiedName = record.name + nested.name
                    if (idx != 0) appendLine(" |")
                    append(qualifiedName)
                    idx++
                }
                appendLine(";")
            }

            appendLine()
            appendLine("export const ${record.name}Companion: BinaryTypeCompanion<${record.name}> & any = {")
            indent {
                appendLine("size: 0,")
                appendLine("interpret(ptr: BufferAndOffset): ${record.name} {")
                indent {
                    appendLine("const tag = ptr.buf.getInt8(ptr.offset);")
                    appendLine("switch (tag) {")
                    addIndentation()

                    val ordinalsSeen = HashSet<Int>()

                    for (child in record.nested) {
                        if (child.implements != record.name) continue

                        val annotation = child.annotations.find { it.name == "Tag" }!!
                        val ordinalValueTok =
                            annotation.attributes.find { it.first == "ordinal" }?.second as Token.Integer

                        if (ordinalValueTok.integer in ordinalsSeen) {
                            reportError(
                                child.location, "Duplicate ordinal detected. " +
                                        "All ordinal values must be unique within a single tagged record!"
                            )
                        }

                        ordinalsSeen.add(ordinalValueTok.integer)

                        appendLine("case ${ordinalValueTok.integer}: return new ${record.name}${child.name}(ptr)")
                    }

                    appendLine("default: throw new Error(\"Invalid ${record.name} ordinal received: \" + tag);")
                    removeIndentation()
                    appendLine("}")
                }
                append("},")
                appendLine("create(buffer: BufferAndOffset): ${record.name} {")
                indent { appendLine("return this.interpret(buffer);") }
                appendLine("},")

                appendLine()
                appendLine("decodeFromJson(allocator: BinaryAllocator, json: unknown): ${record.name} {")
                indent {
                    appendLine("if (typeof json !== \"object\" || json === null) {")
                    indent {
                        appendLine("throw \"Expected an object but found an: \" + json;")
                    }
                    appendLine("}")
                    appendLine("const typeTag = json['type'];")
                    appendLine("if (typeof typeTag !== 'string') throw \"Expected 'type' to be a string\";")
                    appendLine("switch (typeTag) {")
                    indent {
                        val ordinalsSeen = HashSet<String>()

                        for (child in record.nested) {
                            if (child.implements != record.name) continue

                            val annotation = child.annotations.find { it.name == "Tag" }!!
                            val ordinalValueTok =
                                annotation.attributes.find { it.first == "text" }?.second as Token.Text

                            if (ordinalValueTok.text in ordinalsSeen) {
                                reportError(
                                    child.location, "Duplicate type tag detected. " +
                                            "All ordinal values must be unique within a single tagged record!"
                                )
                            }

                            ordinalsSeen.add(ordinalValueTok.text)

                            appendLine("case '${ordinalValueTok.text}': return ${record.name}${child.name}Companion.decodeFromJson(allocator, json);")
                        }

                        appendLine("default: throw new Error(\"Invalid ${record.name} ordinal received: \" + typeTag);")
                    }
                    appendLine("}")
                }
                appendLine("},")
            }
            appendLine("};")
        } else {
            val qualifiedName = buildString {
                if (record.implements != null) append(record.implements)
                append(record.name)
            }

            var offset = 0

            appendLine("export class $qualifiedName implements UBinaryType {")
            indent {
                appendLine("buffer: BufferAndOffset;")
                appendLine("constructor(buffer: BufferAndOffset) {")
                indent {
                    appendLine("this.buffer = buffer;")

                    if (record.implements != null) {
                        offset += 1
                    }
                }
                appendLine("}")
                appendLine()

                for (prop in record.properties) {
                    val propTypeName = tsTypeReference(prop)

                    when (prop.typeModifier) {
                        Parser.Node.TypeModifier.NONE -> {

                            val typeLookup = types.lookupType(ast, prop.type)
                            if (typeLookup is TypeTable.Type.Enumeration) {
                                appendLine("get ${prop.name}(): $propTypeName {")
                                indent {
                                    appendLine("return ${propTypeName}Companion.fromEncoded(this.buffer.buf.getInt16($offset + this.buffer.offset))!;")
                                }
                                appendLine("}")

                                appendLine("set ${prop.name}(value: ${propTypeName}) {")
                                indent {
                                    appendLine("this.buffer.buf.setInt16($offset + this.buffer.offset!, ${prop.type}Companion.encoded(value));")
                                }
                                appendLine("}")
                                appendLine()

                                offset += 2
                            } else {
                                if (shouldAccessThroughPointer(prop)) {
                                    // Nullable primitives, records and strings
                                    val basePropName = if (prop.type == "String") "_${prop.name}" else prop.name

                                    appendLine("get ${basePropName}(): $propTypeName {")
                                    indent {
                                        appendLine("let result: ${tsType(prop.type)} | null = null;")
                                        appendLine("const ptr = this.buffer.buf.getInt32($offset + this.buffer.offset);")
                                        appendLine("if (ptr === 0) result = null;")
                                        appendLine("else {")
                                        indent {
                                            if (isPrimitive(prop.type)) {
                                                append("result = ")
                                                getOrSetPrimitive(false, prop.type, "ptr", null, "this.buffer.buf")
                                            } else {
                                                if (typeLookup is TypeTable.Type.Record && typeLookup.record.isTagged) {
                                                    appendLine("result = ${tsType(prop.type)}Companion.interpret(this.buffer.copyWithOffset(ptr));")
                                                } else {
                                                    appendLine("result = new ${tsType(prop.type)}(this.buffer.copyWithOffset(ptr));")
                                                }
                                            }
                                        }
                                        appendLine("}")

                                        append("return result")
                                        if (!prop.optional) append("!")
                                        appendLine(";")
                                    }
                                    appendLine("}")

                                    appendLine("set ${basePropName}(value: $propTypeName) {")
                                    indent {
                                        appendLine("if (value === null) this.buffer.buf.setInt32($offset + this.buffer.offset, 0);")
                                        appendLine("else {")
                                        indent {
                                            if (isPrimitive(prop.type)) {
                                                val size = primitiveSize(prop.type)
                                                appendLine("const ptr = this.buffer.allocator.allocateDynamic($size);")
                                                appendLine("this.buffer.buf.setInt32($offset + this.buffer.offset, ptr.offset);")
                                                getOrSetPrimitive(
                                                    true,
                                                    prop.type,
                                                    "ptr.offset",
                                                    "value",
                                                    "this.buffer.buf"
                                                )
                                            } else {
                                                appendLine("this.buffer.buf.setInt32($offset + this.buffer.offset, value.buffer.offset);")
                                            }
                                        }
                                        appendLine("}")
                                    }
                                    appendLine("}")

                                    if (prop.type == "String") {
                                        require(basePropName != prop.name)

                                        append("get ${prop.name}(): string")
                                        if (prop.optional) append(" | null")
                                        appendLine(" {")
                                        indent { appendLine("return this.$basePropName?.decode() ?? null;") }
                                        appendLine("}")
                                    }

                                    offset += 4
                                } else {
                                    var size = 0
                                    appendLine("get ${prop.name}(): ${primitiveType(prop.type)} {")
                                    indent {
                                        append("return ")
                                        getOrSetPrimitive(
                                            false,
                                            prop.type,
                                            "$offset + this.buffer.offset",
                                            null,
                                            "this.buffer.buf"
                                        )
                                    }
                                    appendLine("}")

                                    appendLine()
                                    appendLine(
                                        "set ${prop.name}(value: ${primitiveType(prop.type)}) {"
                                    )
                                    indent {
                                        size = getOrSetPrimitive(
                                            true,
                                            prop.type,
                                            "$offset + this.buffer.offset",
                                            "value",
                                            "this.buffer.buf"
                                        )
                                    }
                                    appendLine("}")

                                    offset += size
                                }
                            }
                        }

                        else -> {
                            val containerType =
                                if (prop.typeModifier == Parser.Node.TypeModifier.ARRAY) "BinaryTypeList"
                                else "BinaryTypeDictionary"

                            appendLine("get ${prop.name}(): $propTypeName {")
                            indent {
                                val elemType = tsType(prop.type)
                                appendLine("let result: $containerType<$elemType> | null = null;")
                                appendLine("const ptr = this.buffer.buf.getInt32($offset + this.buffer.offset);")
                                appendLine("if (ptr === 0) result = null;")
                                appendLine("else {")
                                indent {
                                    appendLine("result = new $containerType<$elemType>(${elemType}Companion, this.buffer.copyWithOffset(ptr));")
                                }
                                appendLine("}")

                                append("return result")
                                if (!prop.optional) append("!")
                                appendLine(";")
                            }
                            appendLine("}")
                            appendLine()

                            appendLine("set ${prop.name}(value) {")
                            indent {
                                appendLine("if (value === null) this.buffer.buf.setInt32($offset + this.buffer.offset, 0);")
                                appendLine("else this.buffer.buf.setInt32($offset + this.buffer.offset, value.buffer.offset);")
                            }
                            appendLine("}")

                            offset += 4
                        }
                    }
                    appendLine()
                }

                appendLine("encodeToJson() {")
                indent {
                    appendLine("return {")
                    indent {
                        if (record.implements != null) {
                            val annotation = record.annotations.find { it.name == "Tag" } ?: reportError(
                                record.location,
                                "Mandatory @Tag(text = \"name\", ordinal = 42) annotation is missing"
                            )

                            val name = annotation.attributes.find { it.first == "text" }
                            val nameValueTok = name?.second
                            if (nameValueTok !is Token.Text) {
                                reportError(
                                    annotation.location, "Missing or invalid text attribute in the @Tag " +
                                            "annotation. It must exist with an string value!"
                                )
                            }

                            appendLine("type: \"${nameValueTok.text}\",")
                        }

                        for (prop in record.properties) {
                            append("${prop.name}: ")
                            when (prop.typeModifier) {
                                Parser.Node.TypeModifier.NONE -> {
                                    val lookup = types.lookupType(ast, prop.type)
                                    if (isPrimitive(prop.type) || prop.type == "String") {
                                        append("this.${prop.name}")
                                    } else if (lookup is TypeTable.Type.Enumeration) {
                                        append("this.${prop.name} != null ? ${prop.type}Companion.encoded(this.${prop.name}) : null")
                                    } else {
                                        append("this.${prop.name}?.encodeToJson() ?? null")
                                    }
                                }

                                else -> {
                                    append("this.${prop.name}?.encodeToJson() ?? null")
                                }
                            }
                            appendLine(",")
                        }
                    }
                    appendLine("};")
                }
                appendLine("}")

                appendLine()
                appendLine("static create(")
                indent {
                    appendLine("allocator: BinaryAllocator,")
                    for (prop in record.properties) {
                        val typeName = tsTypeInConstructor(prop)
                        appendLine("${prop.name}: $typeName,")
                    }
                }
                appendLine("): $qualifiedName {")
                indent {
                    appendLine("const result = allocator.allocate(${qualifiedName}Companion);")

                    if (record.implements != null) {
                        val annotation = record.annotations.find { it.name == "Tag" }!!
                        val ordinalValueTok =
                            annotation.attributes.find { it.first == "ordinal" }?.second as Token.Integer

                        appendLine("result.buffer.buf.setInt8(result.buffer.offset, ${ordinalValueTok.integer});")
                    }

                    for (prop in record.properties) {
                        if (prop.type == "String" && prop.typeModifier == Parser.Node.TypeModifier.NONE) {
                            if (prop.optional) {
                                appendLine("if (${prop.name} === null) result._${prop.name} = null;")
                                appendLine("else result._${prop.name} = allocator.allocateText(${prop.name});")
                            } else {
                                appendLine("result._${prop.name} = allocator.allocateText(${prop.name});")
                            }
                        } else {
                            appendLine("result.${prop.name} = ${prop.name};")
                        }
                    }
                    appendLine("return result;")
                }
                appendLine("}")
            }
            appendLine("}")

            appendLine("export const ${qualifiedName}Companion: BinaryTypeCompanion<$qualifiedName> = {")
            indent {
                appendLine("size: $offset,")

                appendLine("decodeFromJson: (allocator, element) => {")
                indent {
                    appendLine("if (typeof element !== \"object\" || element === null) {")
                    indent { appendLine("throw \"Expected an object but found an: \" + element;") }
                    appendLine("}")

                    for (prop in record.properties) {
                        val type = tsTypeInConstructor(prop)
                        appendLine("let ${prop.name}: ${type} | null = null;")

                        appendLine("{")
                        indent {
                            appendLine("const valueForJsonDecode = element['${prop.name}'];")
                            if (prop.optional) {
                                appendLine("if (valueForJsonDecode === null) ${prop.name} = null;")
                                appendLine("else {")
                                addIndentation()
                            }

                            when (prop.typeModifier) {
                                Parser.Node.TypeModifier.NONE -> {
                                    if (isPrimitive(prop.type) || prop.type == "String") {
                                        val primitiveType = if (prop.type == "String") "string" else tsType(prop.type)
                                        appendLine("if (typeof valueForJsonDecode !== '${primitiveType}') throw \"Expected '${prop.name}' to be a $primitiveType\";")
                                        appendLine("${prop.name} = valueForJsonDecode;")
                                    } else {
                                        val lookup = types.lookupType(ast, prop.type)
                                        if (lookup is TypeTable.Type.Enumeration) {
                                            appendLine("if (typeof valueForJsonDecode !== 'string') throw \"Expected '${prop.name}' to be a string\";")
                                            appendLine("${prop.name} = ${prop.type}Companion.fromSerialName(valueForJsonDecode);")
                                        } else {
                                            appendLine("if (typeof valueForJsonDecode !== 'object') throw \"Expected '${prop.name}' to be an object\";")
                                            appendLine("${prop.name} = ${prop.type}Companion.decodeFromJson(allocator, valueForJsonDecode);")
                                        }
                                    }


                                }

                                Parser.Node.TypeModifier.ARRAY -> {
                                    appendLine("if (!Array.isArray(valueForJsonDecode)) throw \"Expected '${prop.name}' to be an array\";")
                                    appendLine("${prop.name} = BinaryTypeList.create(")
                                    indent {
                                        val elemType = tsType(prop.type)
                                        appendLine("${elemType}Companion,")
                                        appendLine("allocator,")
                                        appendLine("valueForJsonDecode.map(it => ${elemType}Companion.decodeFromJson(allocator, it))")
                                    }
                                    appendLine(");")
                                }

                                Parser.Node.TypeModifier.DICTIONARY -> {
                                    val elemType = tsType(prop.type)
                                    appendLine("if (typeof valueForJsonDecode !== 'object') throw \"Expected '${prop.name}' to be an object\";")
                                    appendLine("let builder: Record<string, $elemType> = {};")
                                    appendLine("for (const key of Object.keys(valueForJsonDecode)) {")
                                    indent { appendLine("builder[key] = ${elemType}Companion.decodeFromJson(allocator, valueForJsonDecode[key]);") }
                                    appendLine("}")

                                    appendLine("${prop.name} = BinaryTypeDictionary.create(${elemType}Companion, allocator, builder);")
                                }
                            }

                            if (prop.optional) {
                                removeIndentation()
                                appendLine("}")
                            } else {
                                appendLine("if (${prop.name} === null) throw \"Did not expect '${prop.name}' to be null!\";")
                            }
                        }
                        appendLine("}")
                    }
                    appendLine("return ${qualifiedName}.create(")
                    indent {
                        appendLine("allocator,")
                        for (prop in record.properties) {
                            appendLine("${prop.name},")
                        }
                    }
                    appendLine(");")
                }
                appendLine("},")

                appendLine("create: (buf) => new $qualifiedName(buf),")
            }
            appendLine("};")
        }

        for (nested in record.nested) {
            visitRecord(nested)
        }
    }

    return IndentedStringBuilder().apply {
        repeat(3) { appendLine("// GENERATED CODE - DO NOT MODIFY - See ${ast.fileIdentifier.substringAfterLast("/")}") }
        appendLine()

        appendLine("import { BinaryAllocator, UBinaryType, BinaryTypeCompanion, UText, UTextCompanion, BufferAndOffset, BinaryTypeList, BinaryTypeDictionary } from \"@/UCloud/Messages\";")
        appendLine()
        for (import in ast.imports) {
            // TODO Imports do not work yet for typescript generation
            appendLine("// import dk.sdu.cloud.$import.api.*")
        }

        if (ast.imports.isNotEmpty()) appendLine()

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
