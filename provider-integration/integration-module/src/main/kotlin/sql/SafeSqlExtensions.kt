package dk.sdu.cloud.sql

fun safeSqlParameterList(prefix: String, collection: List<*>): String {
    return collection.mapIndexed { idx, _ -> ":${prefix}_${idx}" }.joinToString(", ")
}

suspend inline fun <reified T> PreparedStatement.bindParameterList(prefix: String, collection: List<T>) {
    for ((idx, item) in collection.withIndex()) {
        val param = "${prefix}_${idx}"
        when (T::class) {
            Int::class -> bindInt(param, item as Int)
            Long::class -> bindLong(param, item as Long)
            String::class -> bindString(param, item as String)
            Boolean::class -> bindBoolean(param, item as Boolean)
            Double::class -> bindDouble(param, item as Double)
            else -> throw IllegalArgumentException("Type cannot be bound to a SQL statement: ${T::class}")
        }
    }
}

/**
 * Usage:
 *
 * ```kotlin
 * val table = listOf(
 *     mapOf("key" to 1, "new_state" to "RUNNING"),
 *     mapOf("key" to 2, "new_state" to "FAILURE"),
 *     mapOf("key" to 4, "new_state" to "SUCCESS"),
 *     mapOf("key" to 5, "new_state" to "RUNNING"),
 * )
 *
 * session.prepareStatement("""
 *     with my_table_expression as (
 *         ${safeSqlTableUpload("update", table)}
 *     )
 *     -- ... Use the CTE ...
 * """).useAndInvokeAndDiscard(
 *     prepare = {
 *         bindTableUpload("update", table)
 *     }
 * )
 * ```
 */
fun safeSqlTableUpload(prefix: String, table: List<Map<String, Any?>>): String {
    var rowIdx = 0
    return table.joinToString(" union all ") {
        buildString {
            append("select ")
            for ((colIdx, entry) in it.entries.withIndex()) {
                if (colIdx != 0) append(", ")
                val (k) = entry
                append(':')
                append(prefix)
                append('_')
                for (charOfKey in k) {
                    if (charOfKey !in 'a'..'z' && charOfKey !in 'A'..'Z' && charOfKey !in '0'..'9' && charOfKey != '_') {
                        throw IllegalArgumentException("Unsafe key $k")
                    }

                    append(charOfKey)
                }
                append('_')
                append(rowIdx)

                append(" as ")
                append(k)
            }
        }.also { rowIdx++ }
    }
}

suspend fun PreparedStatement.bindTableUpload(prefix: String, table: List<Map<String, Any?>>) {
    for ((rowIdx, row) in table.withIndex()) {
        for ((key, value) in row.entries) {
            val param = "${prefix}_${key}_${rowIdx}"
            bindValue(param, value)
        }
    }
}

private suspend fun PreparedStatement.bindValue(param: String, value: Any?) {
    when (value) {
        null -> bindNull(param)
        is Int -> bindInt(param, value)
        is Long -> bindLong(param, value)
        is String -> bindString(param, value)
        is Boolean -> bindBoolean(param, value)
        is Double -> bindDouble(param, value)
        else -> throw IllegalArgumentException("Type cannot be bound to a SQL statement: ${value::class}")
    }
}
