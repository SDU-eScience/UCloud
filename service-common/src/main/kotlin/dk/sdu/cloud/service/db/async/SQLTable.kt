package dk.sdu.cloud.service.db.async

import org.joda.time.LocalDateTime

/**
 * A type safe definition of a SQL table.
 *
 * Example:
 *
 * ```kotlin
 * object Dog : SQLTable("dogs") {
 *     val name = text("name")
 *     val gender = text("gender")
 * }
 * ```
 */
abstract class SQLTable(val tableName: String) {
    private val backingFields = ArrayList<SQLField<*>>()
    val fields get() = backingFields.toList()

    internal fun <K, T : SqlType<K>> addField(
        name: String,
        type: String,
        jdbcType: T,
        notNull: Boolean
    ): SQLField<T> {
        val element = SQLField(name, type, jdbcType, notNull)
        backingFields.add(element)
        return element
    }

    override fun toString(): String = tableName
}

fun SQLTable.varchar(name: String, size: Int, notNull: Boolean = false) =
    addField(name, "varchar($size)", SqlType.TString, notNull)

fun SQLTable.int(
    name: String,
    notNull: Boolean = false
) = addField(name, "int", SqlType.TInt, notNull)

fun SQLTable.long(
    name: String,
    notNull: Boolean = false
) = addField(name, "bigint", SqlType.TLong, notNull)

fun SQLTable.text(
    name: String,
    notNull: Boolean = false
) = addField(name, "text", SqlType.TString, notNull)

fun SQLTable.timestamp(
    name: String,
    notNull: Boolean = false
) = addField(name, "timestamp", SqlType.TTimestamp, notNull)

fun SQLTable.jsonb(
    name: String,
    notNull: Boolean = false
) = addField(name, "jsonb", SqlType.TJSONB, notNull)

fun SQLTable.bool(
    name: String,
    notNull: Boolean = false
) = addField(name, "bool", SqlType.TBoolean, notNull)

sealed class SqlType<T> {
    object TString : SqlType<String>()
    object TBoolean : SqlType<Boolean>()
    object TByte : SqlType<Byte>()
    object TShort : SqlType<Short>()
    object TInt : SqlType<Int>()
    object TLong : SqlType<Long>()
    object TFloat : SqlType<Float>()
    object TDouble : SqlType<Double>()
    object TBytes : SqlType<ByteArray>()
    object TTimestamp : SqlType<LocalDateTime>()
    object TJSONB : SqlType<String>()
}

class SQLField<Type : SqlType<*>>(
    val name: String,
    val type: String,
    val sqlType: Type,
    val notNull: Boolean = false
) {
    init {
        require(name.matches(safeSqlNameRegex)) { "Column contains potentially insecure characters: '$name'" }
    }

    override fun toString(): String = name
}

class SQLRow(private val map: HashMap<SQLField<*>, Any?> = HashMap()) {
    operator fun <KType, T : SqlType<KType>> get(key: SQLField<T>): KType {
        @Suppress("UNCHECKED_CAST")
        return map[key] as KType
    }

    fun <KType, T : SqlType<KType>> getOrNull(key: SQLField<T>): KType? {
        val value = map[key] ?: return null

        @Suppress("UNCHECKED_CAST")
        return value as KType
    }

    operator fun <KType, T : SqlType<KType>> set(key: SQLField<T>, value: KType?) {
        map[key] = value
    }

    fun getUntyped(key: SQLField<*>): Any? {
        return map[key]
    }

    fun keys(): Set<SQLField<*>> = map.keys

    override fun toString(): String = "SQLRow($map)"
}
