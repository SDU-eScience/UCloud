package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.QueryResult
import dk.sdu.cloud.service.Loggable
import org.intellij.lang.annotations.Language
import org.joda.time.LocalDateTime
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Provides an enhanced prepared statement adding support for named parameters.
 *
 * Named parameters use the following syntax: ":PARAMNAME".
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
 *         select :example
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
 *         where id = :id
 *     """
 * )
 * ```
 */
class EnhancedPreparedStatement(
    @Language("sql")
    statement: String
) {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val boundValues = HashSet<String>()
    private val preparedStatement: String
    private val parameters: Array<Any?>

    init {
        val parameterNamesToIndex = HashMap<String, List<Int>>()

        var parameterIndex = 0
        statementInputRegex.findAll(statement).forEach {
            val parameterName = it.groups[2]!!.value
            parameterNamesToIndex[parameterName] =
                (parameterNamesToIndex[parameterName] ?: emptyList()) + listOf(parameterIndex)

            parameterIndex++
        }

        preparedStatement = statementInputRegex.replace(statement) { it.groups[1]!!.value + "?" }
        this.parameterNamesToIndex = parameterNamesToIndex
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
        val indices = parameterNamesToIndex[name] ?: return run {
            log.debug("Unused parameter '$name'")
        }
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

    override fun toString(): String {
        return "EnhancedPreparedStatement(parameterNamesToIndex=$parameterNamesToIndex, boundValues=$boundValues, preparedStatement='$preparedStatement', parameters=${parameters.contentToString()})"
    }

    companion object : Loggable {
        override val log = logger()
        private val statementInputRegex = Regex("(^|[^:])[?:]([a-zA-Z0-9_]+)")
    }
}

suspend inline fun AsyncDBConnection.sendPreparedStatement(
    block: EnhancedPreparedStatement.() -> Unit,
    @Language("sql")
    query: String,
    release: Boolean = false
): QueryResult {
    return EnhancedPreparedStatement(query).also(block).sendPreparedStatement(this, release)
}

private val camelToSnakeRegex = "([a-z])([A-Z]+)".toRegex()
fun String.convertCamelToSnake(): String = replace(camelToSnakeRegex, "$1_$2").lowercase()

fun <T> EnhancedPreparedStatement.parameterList(): SqlBoundDelegate<ArrayList<T>> =
    SqlBoundDelegate(this, ArrayList<T>())
fun <T> EnhancedPreparedStatement.parameter(): SqlBoundDelegate<T> = SqlBoundDelegate(this, null)

class SqlBoundDelegate<T>(
    private val statement: EnhancedPreparedStatement,
    private val defaultValue: T?
) {
    private var value: T? = null

    operator fun getValue(thisRef: Nothing?, property: KProperty<*>): T {
        if (value == null) {
            statement.setParameterUntyped(property.name.convertCamelToSnake(), defaultValue)
            this.value = defaultValue
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    operator fun setValue(thisRef: Nothing?, property: KProperty<*>, value: T) {
        this.value = value
        statement.setParameterUntyped(property.name.convertCamelToSnake(), value)
    }
}
