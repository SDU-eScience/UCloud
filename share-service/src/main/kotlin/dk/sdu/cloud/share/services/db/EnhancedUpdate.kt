package dk.sdu.cloud.share.services.db

data class StringWithNoUserInput(val string: String) {
    override fun toString(): String = string
}

fun String.thisStringHasNoUserInput(): StringWithNoUserInput = StringWithNoUserInput(this)

suspend fun AsyncDBConnection.update(
    table: String,
    columnToValue: Map<String, Any?>,

    parameters: EnhancedPreparedStatement.() -> Unit,
    where: StringWithNoUserInput
) {
    if (table.matches(columnRegex)) throw IllegalArgumentException("Insecure table name: $table")
    // Quick sanity check (columns should not come from user data)
    val keys = columnToValue.keys.toList()
    require(keys.isNotEmpty())

    keys.forEach {
        if (!it.matches(columnRegex)) throw IllegalArgumentException("Insecure column name $it")
    }

    sendPreparedStatement(
        {
            parameters()
            keys.forEach { setParameterUntyped(it, columnToValue[it]) }
        },

        "update $table set ${keys.joinToString(",") { "$it = ?$it" }} $where"
    )
}

suspend fun AsyncDBConnection.update(
    table: SQLTable,
    row: SQLRow,
    parameters: EnhancedPreparedStatement.() -> Unit,
    where: StringWithNoUserInput
) {
    val keys = row.keys().toList()
    require(keys.isNotEmpty())

    sendPreparedStatement(
        {
            parameters()
            keys.forEach { setParameterUntyped(it.name, row.getUntyped(it)) }
        },

        "update $table set ${keys.joinToString(",") { "${it.name} = ?${it.name}" }} where $where"
    )
}
