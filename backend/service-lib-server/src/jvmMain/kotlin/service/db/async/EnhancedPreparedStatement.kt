package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.QueryResult
import dk.sdu.cloud.debug.DebugContext
import dk.sdu.cloud.debug.DebugMessage
import dk.sdu.cloud.service.Loggable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import org.joda.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
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
    private val rawStatement: String
) {
    private val parameterNamesToIndex: Map<String, List<Int>>
    private val boundValues = HashSet<String>()
    private val preparedStatement: String
    private val parameters: Array<Any?>
    private val rawParameters = HashMap<String, Any?>()

    init {
        val parameterNamesToIndex = HashMap<String, List<Int>>()

        var parameterIndex = 0
        statementInputRegex.findAll(rawStatement).forEach {
            val parameterName = it.groups[2]!!.value
            parameterNamesToIndex[parameterName] =
                (parameterNamesToIndex[parameterName] ?: emptyList()) + listOf(parameterIndex)

            parameterIndex++
        }

        preparedStatement = statementInputRegex.replace(rawStatement) { it.groups[1]!!.value + "?" }
        this.parameterNamesToIndex = parameterNamesToIndex
        parameters = Array(parameterIndex) { null }
    }

    fun retain(vararg names: String) {
        val newMap = rawParameters.filterKeys { it in names }
        rawParameters.clear()
        rawParameters.putAll(newMap)
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
        val indices = parameterNamesToIndex[name] ?: return
        for (index in indices) {
            parameters[index] = value
        }
        boundValues.add(name)
        rawParameters[name] = value
    }

    suspend fun sendPreparedStatement(session: AsyncDBConnection, release: Boolean = false): QueryResult {
        val context = DebugContext.Job(
            session.context.id + "-" + queryCounter.getAndIncrement(),
            session.context.id
        )

        session.debug?.sendMessage(
            DebugMessage.DatabaseQuery(
                context,
                rawStatement,
                JsonObject(
                    rawParameters.map { (param, value) -> param to JsonPrimitive(value.toString()) }.toMap()
                )
            )
        )

        check(boundValues.size == parameterNamesToIndex.keys.size) {
            val missingSetParameters = parameterNamesToIndex.keys.filter { it !in boundValues }
            val missingSqlParameters = boundValues.filter { it !in parameterNamesToIndex.keys }

            buildString {
                if (missingSetParameters.isNotEmpty()) {
                    append("Keys missing from `setParameter`: $missingSetParameters")
                }
                if (missingSqlParameters.isNotEmpty()) {
                    append("Keys missing from query: $missingSqlParameters")
                }
            }
        }
        val response = session.sendPreparedStatement(preparedStatement, parameters.toList(), release)
        session.debug?.sendMessage(DebugMessage.DatabaseResponse(context))
        return response
    }

    inline fun <T> splitCollection(collection: Collection<T>, builder: SplitBuilder<T>.() -> Unit) {
        collection.split(builder)
    }

    inline fun <T> Collection<T>.split(builder: SplitBuilder<T>.() -> Unit) {
        SplitBuilder(this, this@EnhancedPreparedStatement).also(builder).endSplitting()
    }

    override fun toString(): String {
        return buildString {
            appendLine(rawStatement)
            appendLine()
            for ((param, value) in rawParameters) {
                append(param)
                append(" = ")
                append(value.toString())
                appendLine()
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private val statementInputRegex = Regex("(^|[^:])[?:]([a-zA-Z0-9_]+)")
        private val queryCounter = AtomicInteger(0)
    }
}

suspend inline fun AsyncDBConnection.sendPreparedStatement(
    block: EnhancedPreparedStatement.() -> Unit,
    @Language("sql")
    query: String,
    release: Boolean = false,
    debug: Boolean = false,
): QueryResult {
    val statement = EnhancedPreparedStatement(query)
    statement.block()
    if (debug) EnhancedPreparedStatement.log.debug(statement.toString())
    return statement.sendPreparedStatement(this, release)
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

class SplitBuilder<T>(
    private val collection: Collection<T>,
    private val statement: EnhancedPreparedStatement
) {
    private val splits = ArrayList<Pair<String, (T) -> Any?>>()

    fun <R> into(name: String, splitter: (T) -> R): SplitBuilder<T> {
        splits.add(Pair(name, splitter))
        return this
    }

    fun endSplitting() {
        val allArgumentLists = HashMap<String, ArrayList<Any?>>()
        for ((name) in splits) {
            val args = ArrayList<Any?>()
            allArgumentLists[name] = args
            statement.setParameter(name, args)
        }

        for (item in collection) {
            for ((name, splitter) in splits) {
                allArgumentLists.getValue(name).add(splitter(item))
            }
        }
    }
}
