package dk.sdu.cloud.sql

import dk.sdu.cloud.DB_CONNECTION_POOL_SIZE
import dk.sdu.cloud.defaultMapper
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer

// NOTE(Dan): This is just something I am trying out. This might be an awful idea, or it might be a significantly
// better idea than the `_to_json` procedures we currently have in the backend.
data class TemporaryView<T>(
    val serializer: KSerializer<T>,
    private val doRegister: suspend (session: DBContext.Connection) -> Unit
) {
    private val registeredIn = BooleanArray(DB_CONNECTION_POOL_SIZE)
    private val mutex = Mutex()

    suspend fun createIfNeeded(session: DBContext.Connection) {
        if (session is JdbcConnection) {
            if (!registeredIn[session._connectionTicket]) {
                mutex.withLock {
                    if (registeredIn[session._connectionTicket]) return
                    doRegister(session)
                    registeredIn[session._connectionTicket] = true
                }
            }
        } else {
            TODO("TemporaryView not yet implemented for non JDBC connections")
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun <T> generate(
            serializer: KSerializer<T>,
            tableName: String,
            propertyToColumnGenerator: (property: String) -> String = ::defaultSqlPropertyExtractor,
        ): TemporaryView<T> {
            return TemporaryView(serializer) { session ->
                session.prepareStatement(
                    buildString {
                        append("create or replace temporary view ${tableName}_json as select row.*, jsonb_build_object(")
                        for (i in 0 until serializer.descriptor.elementsCount) {
                            val name = serializer.descriptor.getElementName(i)
                            if (i != 0) append(", ")
                            append("'$name', ")
                            append(propertyToColumnGenerator(name))
                        }
                        append(") as serialized from $tableName row")
                    }
                ).useAndInvokeAndDiscard()
            }
        }
    }
}

fun timestampExtractor(property: String): String {
    return "(extract(epoch from $property) * 1000)::int8"
}

fun stringIdExtractor(property: String): String {
    return "$property::text"
}

fun camelToSnake(text: String): String {
    val builder = StringBuilder()
    for (char in text) {
        if (char.isUpperCase()) {
            builder.append('_')
            builder.append(char.lowercaseChar())
        } else {
            builder.append(char)
        }
    }
    return builder.toString()
}

fun defaultSqlPropertyExtractor(prop: String): String {
    return when (prop) {
        "createdAt", "modifiedAt", "updatedAt", "ts", "timestamp" -> timestampExtractor(prop)
        "id" -> stringIdExtractor(prop)
        else -> camelToSnake(prop)
    }
}

fun snakeToCamel(text: String): String {
    val builder = StringBuilder()
    var i = 0
    while (i < text.length) {
        val char = text[i]
        if (char == '_') {
            if (i + 1 < text.length) {
                builder.append(text[i + 1].uppercaseChar())
            }
            i += 2
        } else {
            builder.append(char)
            i++
        }
    }
    return builder.toString()
}

suspend fun <T> DBContext.Connection.queryJson(
    view: TemporaryView<T>,
    query: String,
    prepare: suspend PreparedStatement.() -> Unit = {}
): List<T> {
    val session = this
    view.createIfNeeded(session)
    val rows = ArrayList<T>()
    session.prepareStatement(query).useAndInvoke(
        prepare = { prepare() },
        readRow = { row ->
            val serialized = row.getString(0)
            if (serialized != null) {
                rows.add(defaultMapper.decodeFromString(view.serializer, serialized))
            }
        }
    )
    return rows
}
