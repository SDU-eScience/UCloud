package dk.sdu.cloud.person.util

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dk.sdu.cloud.util.DbUtils")

class CommonColumns(
        // TODO Timestamps are bound to event time ?
        val modifiedAt: Column<DateTime>,
        val createdAt: Column<DateTime>,
        val markedForDelete: Column<Int>,
        val active: Column<Int>
)

fun Table.commonAttributes(): CommonColumns =
        CommonColumns(
                modifiedAt = datetime("modified_ts"),
                createdAt = datetime("created_ts"),
                markedForDelete = integer("markedfordelete"),
                active = integer("active")
        )

fun <R> Query.mapSingle(mapper: (ResultRow) -> R): R? = toList().singleOrNull()?.let(mapper)
fun <R> Query.map(mapper: (ResultRow) -> R): List<R> = toList().map(mapper)

interface EnumCache<in K : Any, E : Enum<E>> {
    fun resolveById(id: K): E
}

class EnumCacheImpl<K : Any, E : Enum<E>, T : Table, N>(
        private val table: T,
        private val idColumn: Column<K>,
        private val nameColumn: Column<N>,
        private val defaultValue: E,
        private val lookupTable: Map<N, E>
) : EnumCache<K, E> {
    private var inMemoryCache: Map<K, E> = emptyMap()

    override fun resolveById(id: K): E {
        log.debug("Resolving enum of type ${defaultValue.javaClass} with id $id")
        if (id !in inMemoryCache) {
            log.debug("Cache does not contain the entry. Current cache: $inMemoryCache")
            val rows = transaction { table.selectAll().toList() }
            inMemoryCache = rows.map {
                Pair(it[idColumn], lookupTable.getOrDefault(it[nameColumn], defaultValue))
            }.toMap()
            log.debug("New cache: $inMemoryCache")
        }
        return inMemoryCache[id] ?: throw IllegalArgumentException("No such value exists with id: $id")
    }
}

inline fun <K : Any, N : Any, reified E : Enum<E>, T : Table> T.enumCache(
        idColumn: Column<K>,
        nameColumn: Column<N>,
        defaultValue: E,
        noinline enumKeyProvider: (E) -> N
): EnumCache<K, E> {
    val table = this
    val allValues = enumValues<E>()
    val lookupTable = allValues.associateBy(enumKeyProvider)
    return EnumCacheImpl(table, idColumn, nameColumn, defaultValue, lookupTable)
}

interface EnumTable<in K : Any, E : Enum<E>> {
    val enumResolver: EnumCache<K, E>

    fun resolveById(id: K): E = enumResolver.resolveById(id)
}
