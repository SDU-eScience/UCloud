package dk.sdu.cloud.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jetbrains.exposed.sql.*
import org.postgresql.util.PGobject
import java.sql.PreparedStatement

var defaultExposedObjectMapper = jacksonObjectMapper()

inline fun <reified T : Any> Table.jsonb(
    name: String,
    mapper: ObjectMapper = defaultExposedObjectMapper
): Column<T> {
    return jsonb(name, T::class.java, mapper)
}

fun <T : Any> Table.jsonb(
    name: String,
    klass: Class<T>,
    mapper: ObjectMapper = defaultExposedObjectMapper
): Column<T> {
    return registerColumn(name, Json(klass, mapper))
}

class Json<out T : Any>(private val klass: Class<T>, private val mapper: ObjectMapper) : ColumnType() {
    override fun sqlType() = "jsonb"

    override fun setParameter(stmt: PreparedStatement, index: Int, value: Any?) {
        stmt.setObject(index, PGobject().apply {
            type = "jsonb"
            this.value = value as String
        })
    }

    override fun valueFromDB(value: Any): Any {
        value as PGobject
        return try {
            mapper.readValue(value.value, klass)
        } catch (e: Exception) {
            throw e
        }
    }

    override fun notNullValueToDB(value: Any): Any = mapper.writeValueAsString(value)
    override fun nonNullValueToString(value: Any): String = "'${notNullValueToDB(value)}'"
}

class JSONContainsKeyOp(private val expr: Expression<*>, private val key: Expression<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)} ? ${key.toSQL(queryBuilder)}"
}

class JSONContainsAnyKeyOp(private val expr: Expression<*>, private val keys: List<Expression<*>>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        val arrayBody = keys.joinToString(", ") { it.toSQL(queryBuilder) }
        return "${expr.toSQL(queryBuilder)} ?| array[$arrayBody]"
    }
}

class JSONContainsAllKeyOp(private val expr: Expression<*>, private val keys: List<Expression<*>>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String {
        val arrayBody = keys.joinToString(", ") { it.toSQL(queryBuilder) }
        return "${expr.toSQL(queryBuilder)} ?& array[$arrayBody]"
    }
}

class JSONCastOp<T>(
    override val columnType: IColumnType,
    private val expr: Expression<String>
) : ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)}::jsonb"
}

class JSONContainsRight(private val expr1: Expression<*>, private val expr2: Expression<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String =
        "${expr1.toSQL(queryBuilder)} @> ${expr2.toSQL(queryBuilder)}"
}

class JSONContainsLeft(private val expr1: Expression<*>, private val expr2: Expression<*>) : Op<Boolean>() {
    override fun toSQL(queryBuilder: QueryBuilder): String =
        "${expr1.toSQL(queryBuilder)} <@ ${expr2.toSQL(queryBuilder)}"
}

class JSONLookup<T>(
    override val columnType: IColumnType,
    private val expr: Expression<*>,
    private val key: Expression<*>
) : ExpressionWithColumnType<T>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)}->${key.toSQL(queryBuilder)}"
}

class JSONLookupAsText(private val expr: Expression<*>, private val key: Expression<*>) : Op<String>() {
    override fun toSQL(queryBuilder: QueryBuilder): String = "${expr.toSQL(queryBuilder)}->>${key.toSQL(queryBuilder)}"
}

infix fun Expression<*>.jsonContainsKey(key: Expression<*>): Op<Boolean> {
    return JSONContainsKeyOp(this, key)
}

infix fun Expression<*>.jsonContainsAny(keys: List<String>): Op<Boolean> {
    return JSONContainsAnyKeyOp(this, keys.map { stringParam(it) })
}

infix fun Expression<*>.jsonContainsAll(keys: List<String>): Op<Boolean> {
    return JSONContainsAllKeyOp(this, keys.map { stringParam(it) })
}

inline fun <reified T : Any> castToJson(
    expr: Expression<String>,
    mapper: ObjectMapper = defaultExposedObjectMapper
): ExpressionWithColumnType<T> {
    return JSONCastOp(Json(T::class.java, mapper), expr)
}

inline fun <reified T : Any> literalJson(
    value: T,
    mapper: ObjectMapper = defaultExposedObjectMapper
): ExpressionWithColumnType<T> {
    return castToJson(stringLiteral(mapper.writeValueAsString(value)), mapper)
}

infix fun Expression<*>.jsonContainsRight(other: Expression<*>): Op<Boolean> {
    return JSONContainsRight(this, other)
}

infix fun Expression<*>.jsonContainsLeft(other: Expression<*>): Op<Boolean> {
    return JSONContainsLeft(this, other)
}

inline fun <reified T : Any> Expression<*>.jsonLookup(
    key: Expression<*>,
    mapper: ObjectMapper = defaultExposedObjectMapper
): ExpressionWithColumnType<T> {
    return JSONLookup(Json(T::class.java, mapper), this, key)
}

inline fun <reified T : Any> Expression<*>.jsonLookup(
    key: Int,
    mapper: ObjectMapper = defaultExposedObjectMapper
): ExpressionWithColumnType<T> {
    return JSONLookup(Json(T::class.java, mapper), this, intLiteral(key))
}

inline fun <reified T : Any> Expression<*>.jsonLookup(
    key: String,
    mapper: ObjectMapper = defaultExposedObjectMapper
): ExpressionWithColumnType<T> {
    return JSONLookup(Json(T::class.java, mapper), this, stringParam(key))
}
