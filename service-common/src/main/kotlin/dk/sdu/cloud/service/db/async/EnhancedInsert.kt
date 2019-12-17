package dk.sdu.cloud.service.db.async

/**
 * Provides a short hand for inserting a complete row in a table.
 *
 * Example:
 *
 * ```kotlin
 *  lateinit var connection: AsyncDBConnection
 *
 *  connection.insert(
 *      "dogs",
 *      mapOf(
 *          "name" to "Fie",
 *          "gender" to "female"
 *      )
 *  )
 * ```
 */
suspend fun AsyncDBConnection.insert(table: String, columnToValue: Map<String, Any?>) {
    if (table.matches(safeSqlNameRegex)) throw IllegalArgumentException("Insecure table name: $table")

    // Quick sanity check (columns should not come from user data)
    val keys = columnToValue.keys.toList()
    keys.forEach {
        require(it.matches(safeSqlNameRegex)) { "Insecure column name $it" }
    }

    sendPreparedStatement(
        "insert into $table (${keys.joinToString(",")}) values (${keys.joinToString(",") { "?" }})",
        keys.map { columnToValue[it] }
    )
}

/**
 * Provides a short hand for inserting a complete row in a table.
 *
 * Example:
 *
 * ```kotlin
 * lateinit var connection: AsyncDBConnection
 *
 * object Dog : SQLTable("dogs") {
 *     val name = text("name")
 *     val gender = text("gender")
 * }
 *
 * connection.insert(Dog) {
 *     set(Dog.name, "Fie")
 *     set(Dog.gender, "female")
 * }
 * ```
 */
suspend fun AsyncDBConnection.insert(table: SQLTable, block: SQLRow.() -> Unit) {
    val row = SQLRow().also(block)
    val keys = row.keys().toList()
    sendPreparedStatement(
        "insert into $table (${keys.joinToString(",") { it.name }}) values (${keys.joinToString(",") { "?" }})",
        keys.map { row.getUntyped(it) }
    )
}
