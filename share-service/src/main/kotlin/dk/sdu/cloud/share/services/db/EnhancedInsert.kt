package dk.sdu.cloud.share.services.db

val columnRegex = Regex("[a-zA-Z0-9_]+")

suspend fun AsyncDBConnection.insert(table: String, columnToValue: Map<String, Any?>) {
    if (table.matches(columnRegex)) throw IllegalArgumentException("Insecure table name: $table")

    // Quick sanity check (columns should not come from user data)
    val keys = columnToValue.keys.toList()
    keys.forEach {
        if (!it.matches(columnRegex)) throw IllegalArgumentException("Insecure column name $it")
    }

    sendPreparedStatement(
        "insert into $table (${keys.joinToString(",")}) values (${keys.joinToString(",") { "?" }})",
        keys.map { columnToValue[it] }
    )
}

suspend fun AsyncDBConnection.insert(table: SQLTable, block: SQLRow.() -> Unit) {
    val row = SQLRow().also(block)
    val keys = row.keys().toList()
    sendPreparedStatement(
        "insert into $table (${keys.joinToString(",") { it.name }}) values (${keys.joinToString(",") { "?" }})",
        keys.map { row.getUntyped(it) }
    )
}
