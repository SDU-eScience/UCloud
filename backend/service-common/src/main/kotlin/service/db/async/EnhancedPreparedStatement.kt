package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.QueryResult
import java.time.LocalDateTime

/**
 * Provides an enhanced prepared statement adding support for named parameters.
 *
 * Named parameters use the following syntax: "?PARAMNAME".
 *
 * Examples:
 *
 * ```kotlin
 * lateinit var connection: AsyncDBConnection
 *
 * connection.sendPreparedStatement(
 *     {
 *         setParameter("example", "Hello, World")
 *     },
 *
 *     """
 *         select ?example
 *     """
 * )
 * ```
 *
 * ```kotlin
 * lateinit var connection: AsyncDBConnection
 *
 * connection.sendPreparedStatement(
 *     {
 *         setParameter("id", idParameter)
 *     },
 *
 *     """
 *         select *
 *         from my_table
 *         where id = ?id
 *     """
 * )
 * ```
 */
class EnhancedPreparedStatement(statement: String) {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val boundValues = HashSet<String>()
    private val preparedStatement: String
    private val parameters: Array<Any?>

    init {
        val parameterNamesToIndex = HashMap<String, List<Int>>()

        val queryBuilder = StringBuilder()
        var parameterIndex = 0
        var stringIndex = 0
        while (stringIndex < statement.length) {
            // Find the next parameter by looking for a '?'
            val nextParameter = statement.indexOf('?', stringIndex)
            if (nextParameter == -1) {
                // We're at the end of the string. We just append the remainder to the query.
                queryBuilder.append(statement.substring(stringIndex))
                break
            }

            // Add everything up to and including the '?'. We use this for the prepared statement.
            queryBuilder.append(statement.substring(stringIndex, nextParameter + 1)) // include '?'

            // Parse the parameter name. We only allow alphanumeric and underscores.
            val endOfParameterName = statement.substring(nextParameter + 1)
                .indexOfFirst { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '_' }
                .takeIf { it != -1 }
                ?.let { it + nextParameter + 1 }
                ?: statement.length

            // Write down the parameter name and move past it
            val parameterName = statement.substring(nextParameter + 1, endOfParameterName)
            stringIndex = endOfParameterName

            parameterNamesToIndex[parameterName] =
                (parameterNamesToIndex[parameterName] ?: emptyList()) + listOf(parameterIndex)

            parameterIndex++
        }

        this.parameterNamesToIndex = parameterNamesToIndex
        preparedStatement = queryBuilder.toString()
        parameters = Array(parameterIndex) { null }
    }

    fun setParameterAsNull(name: String) {
        setParameterUntyped(name, null)
    }

    fun setParameter(name: String, value: String?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Boolean?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Byte?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Short?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Int?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Long?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Float?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: List<Any?>?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: Double?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: ByteArray?) {
        setParameterUntyped(name, value)
    }

    fun setParameter(name: String, value: LocalDateTime?) {
        setParameterUntyped(name, value)
    }

    fun setParameterUntyped(name: String, value: Any?) {
        val indices = parameterNamesToIndex[name] ?: throw IllegalArgumentException("Unknown parameter '$name'")
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
    }

    suspend fun sendPreparedStatement(session: AsyncDBConnection, release: Boolean = false): QueryResult {
        check(boundValues.size == parameterNamesToIndex.keys.size) {
            "boundValues.size != parameters.size. " +
                    "boundValues: ${boundValues}, " +
                    "parameters: ${parameterNamesToIndex.keys}"
        }
        return session.sendPreparedStatement(preparedStatement, parameters.toList(), release)
    }
}

suspend inline fun AsyncDBConnection.sendPreparedStatement(
    block: EnhancedPreparedStatement.() -> Unit,
    query: String,
    release: Boolean = false
): QueryResult {
    return EnhancedPreparedStatement(query).also(block).sendPreparedStatement(this, release)
}
