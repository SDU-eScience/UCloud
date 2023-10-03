package dk.sdu.cloud.service.db.async

import com.github.jasync.sql.db.QueryResult
import com.github.jasync.sql.db.postgresql.exceptions.GenericDatabaseException
import dk.sdu.cloud.debug.MessageImportance
import dk.sdu.cloud.debug.databaseQuery
import dk.sdu.cloud.debug.databaseResponse
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.systemName
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Summary
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.intellij.lang.annotations.Language
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.random.Random
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

    private val slowSampleParameters = Collections.synchronizedSet(HashSet<String>()).toMutableSet()

    suspend fun sendPreparedStatement(
        session: AsyncDBConnection,
        release: Boolean = false,
        tagName: String = defaultTag,
    ): QueryResult {
        try {
            val debugQueryParameters = JsonObject(
                rawParameters.map { (param, value) -> param to JsonPrimitive(value.toString()) }.toMap()
            )

            val start = Time.now()
            session.debug.system.databaseQuery(
                MessageImportance.THIS_IS_NORMAL,
                debugQueryParameters,
                rawStatement
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
            val end = Time.now()
            if (end - start > 3_000) {
                log.warn("'${tagName}' took a long time to execute (${end - start}ms).")
                runCatching {
                    if (tagName !in slowSampleParameters) {
                        slowSampleParameters.add(tagName)
                        if (debug) {
                            File("/tmp/slowqueries/${tagName}_params.json")
                                .also { it.parentFile.mkdirs() }
                                .also {
                                    it.writeText(
                                        defaultMapper.encodeToString(
                                            JsonObject(
                                                rawParameters.map { (param, value) ->
                                                    param to JsonPrimitive(value.toString())
                                                }.toMap()
                                            )
                                        )
                                    )
                                }
                        }

                        File("/tmp/slowqueries/${tagName}_query.json")
                            .also { it.parentFile.mkdirs() }
                            .also { it.writeText(rawStatement) }
                    }
                }
            }

            val duration = end - start

            if (tagName != defaultTag && !tagName.contains(" ")) {
                querySummary.labels(tagName).observe(duration.toDouble())
            }

            session.debug.system.databaseResponse(
                importance = when {
                    duration >= 300 -> MessageImportance.THIS_IS_WRONG
                    duration >= 150 -> MessageImportance.THIS_IS_ODD
                    else -> MessageImportance.THIS_IS_NORMAL
                },
                responseTime = duration
            )
            return response
        } catch (ex: GenericDatabaseException) {
            if (ex.message?.contains("invalid input syntax") == true) {
                throw RuntimeException("Invalid query!\n\tQuery=${rawStatement}", ex)
            }

            throw ex
        }
    }

    inline fun <T> splitCollection(collection: Collection<T>, builder: SplitBuilder<T>.() -> Unit) {
        collection.split(builder)
    }

    inline fun <T> Iterable<T>.split(builder: SplitBuilder<T>.() -> Unit) {
        SplitBuilder(this.iterator(), this@EnhancedPreparedStatement).also(builder).endSplitting()
    }

    inline fun <T> Sequence<T>.split(builder: SplitBuilder<T>.() -> Unit) {
        SplitBuilder(this.iterator(), this@EnhancedPreparedStatement).also(builder).endSplitting()
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
        var debug = false

        private val querySummary = Summary.build()
            .namespace(systemName)
            .subsystem("database")
            .quantile(0.5, 0.01)
            .quantile(0.75, 0.01)
            .quantile(0.99, 0.01)
            .labelNames("query")
            .name("query_latency_milliseconds")
            .help("The latency of a named query in milliseconds")
            .register()

    }
}

suspend inline fun AsyncDBConnection.sendPreparedStatement(
    block: EnhancedPreparedStatement.() -> Unit,
    @Language("sql")
    query: String,
    tagName: String = "Untitled query",
    release: Boolean = false,
    debug: Boolean = false,
): QueryResult {
    val statement = EnhancedPreparedStatement(query)
    statement.block()
    if (debug) EnhancedPreparedStatement.log.info(statement.toString())
    return statement.sendPreparedStatement(this, release, tagName)
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
    private val collection: Iterator<T>,
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

private const val defaultTag = "Untitled query"
