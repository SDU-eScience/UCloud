package dk.sdu.cloud.file.ucloud.api

import dk.sdu.cloud.*
import dk.sdu.cloud.calls.*
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.UFile
import io.ktor.http.*
import kotlinx.serialization.Serializable


/**
 * @see AllOf
 */
typealias PredicateCollection<Pred> = AllOf<Pred>

/**
 * A collection of predicates. This predicate will become true if all of the sub predicates are true.
 */
@TSDefinition("""
export interface AllOf<Pred> {
    allOf: AnyOf<Pred>[]
}
""")
@Serializable
data class AllOf<Pred : Any>(val allOf: List<AnyOf<Pred>>) {
    init {
        if (allOf.isEmpty()) throw IllegalArgumentException("allOf cannot be empty")
    }

    companion object {
        fun <Pred : Any> with(vararg list: Pred): PredicateCollection<Pred> = AllOf(list.map { AnyOf(listOf(it)) })
    }
}

/**
 * A collection of predicates. This predicate will become true if any of the sub predicates are true.
 */
@TSDefinition("""
export interface AnyOf<Pred> {
    anyOf: Pred[],
    negate?: boolean,
}
""")
@Serializable
data class AnyOf<Pred : Any>(val anyOf: List<Pred>, val negate: Boolean = false) {
    init {
        if (anyOf.isEmpty()) throw IllegalArgumentException("anyOf cannot be empty")
    }

    companion object {
        fun <Pred : Any> with(vararg list: Pred): PredicateCollection<Pred> = AllOf(listOf(AnyOf(list.toList())))
    }
}

/**
 * An operator used in [Comparison].
 *
 * @see Comparison
 */
@Serializable
enum class ComparisonOperator {
    /**
     * The greater than ">" operator
     */
    GREATER_THAN,

    /**
     * The greater than or equals ">=" operator
     */
    GREATER_THAN_EQUALS,

    /**
     * The less than "<" operator
     */
    LESS_THAN,

    /**
     * The less than or equals "<=" operator
     */
    LESS_THAN_EQUALS,

    /**
     * Will override any other [ComparisonOperator] within a [PredicateCollection]
     */
    EQUALS
}

/**
 * A comparison between a field and a concrete value
 */
@Serializable
data class Comparison<Value : Any>(
    /**
     * The value to compare with
     */
    val value: Value,

    /**
     * The operator to use for comparison
     */
    val operator: ComparisonOperator
)

/**
 * A query for files. It is used in various request classes.
 */
@Serializable
data class FileQuery(
    /**
     * A list of roots to use.
     *
     * Only files that are within one of the roots will be considered in results.
     *
     * This property cannot be empty. To search the entire file system use "/".
     */
    val roots: List<String>,

    /**
     * Query for file names. Only the file name is considered.
     *
     * The query is allowed to do expansions, making it useful for end-user queries.
     */
    val fileNameQuery: List<String>? = null,

    /**
     * Predicates for file names. Only exact matches are considered.
     */
    val fileNameExact: PredicateCollection<String>? = null,

    /**
     * Predicate for file extensions. Only exact matches are considered.
     */
    val extensions: PredicateCollection<String>? = null,

    /**
     * Predicate for file types. Only exact matches are considered.
     */
    val fileTypes: PredicateCollection<FileType>? = null,

    /**
     * Predicate for file depth. Only exact matches are considered.
     */
    val fileDepth: PredicateCollection<Comparison<Int>>? = null,

    /**
     * Predicate for [StorageFile.size]. Only exact matches are considered.
     */
    val fileSize: PredicateCollection<Comparison<Long>>? = null
) {
    init {
        if (roots.isEmpty()) throw IllegalArgumentException("roots cannot be empty")
    }
}

/**
 * Interface for requests the includes a [FileQuery]
 */
interface WithFileQuery {
    val query: FileQuery
}

/**
 * A request for numeric stats
 *
 * @see NumericStatistics
 */
@Serializable
data class NumericStatisticsRequest(
    val calculateMean: Boolean = false,
    val calculateMinimum: Boolean = false,
    val calculateMaximum: Boolean = false,
    val calculateSum: Boolean = false,
    val percentiles: List<Double> = emptyList()
)

/**
 * A response for numeric stats
 *
 * @see NumericStatisticsRequest
 */
@Serializable
data class NumericStatistics(
    val mean: Double? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val sum: Double? = null,
    val percentiles: List<Double> = emptyList()
)

/**
 * @see [QueryDescriptions.query]
 */
@Serializable
data class QueryRequest(
    override val query: FileQuery,

    val sortBy: SortRequest? = null,

    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithFileQuery, WithPaginationRequest

/**
 * @see [QueryDescriptions.query]
 */
typealias QueryResponse = Page<UFile>

/**
 * @see [QueryDescriptions.statistics]
 */
@Serializable
data class StatisticsRequest(
    override val query: FileQuery,

    // This is currently very limited. We can figure out what we need later.
    val size: NumericStatisticsRequest? = null,
    val fileDepth: NumericStatisticsRequest? = null
) : WithFileQuery

/**
 * @see [QueryDescriptions.statistics]
 */
@Serializable
data class StatisticsResponse(
    val count: Long,
    val size: NumericStatistics? = null,
    val fileDepth: NumericStatistics? = null
)

@Serializable
data class SortRequest(
    val field: SortableField,
    val direction: SortDirection
)

@Serializable
enum class SortableField {
    FILE_NAME,
    FILE_TYPE,
    SIZE
}

@Serializable
enum class SortDirection {
    ASCENDING,
    DESCENDING
}
