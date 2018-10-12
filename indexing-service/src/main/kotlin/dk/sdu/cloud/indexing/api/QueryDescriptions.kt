package dk.sdu.cloud.indexing.api

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.Roles
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.client.bindEntireRequestFromBody
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.WithPaginationRequest
import io.ktor.http.HttpMethod

/**
 * @see AllOf
 */
typealias PredicateCollection<Pred> = AllOf<Pred>

/**
 * A collection of predicates. This predicate will become true if all of the sub predicates are true.
 */
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
     * Predicates for [EventMaterializedStorageFile.id]
     */
    val id: PredicateCollection<String>? = null,

    /**
     * Predicates for [EventMaterializedStorageFile.owner]
     */
    val owner: PredicateCollection<String>? = null,

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
     * Predicate for created at. Only exact matches are considered.
     */
    val createdAt: PredicateCollection<Comparison<Long>>? = null,

    /**
     * Predicate for modified at. Only exact matches are considered.
     */
    val modifiedAt: PredicateCollection<Comparison<Long>>? = null,

    /**
     * Predicate for sensitivity. Only exact matches are considered.
     */
    val sensitivity: PredicateCollection<SensitivityLevel>? = null,

    /**
     * Predicate for annotations. Only exact matches are considered.
     */
    val annotations: PredicateCollection<String>? = null,

    /**
     * Predicate for [EventMaterializedStorageFile.isLink]. Only exact matches are considered.
     */
    val fileIsLink: Boolean? = null,

    /**
     * Predicate for [EventMaterializedStorageFile.linkTarget]. Only exact matches are considered.
     */
    val linkTarget: PredicateCollection<String>? = null,

    /**
     * Predicate for [EventMaterializedStorageFile.linkTargetId]. Only exact matches are considered.
     */
    val linkTargetId: PredicateCollection<String>? = null,

    /**
     * Predicate for [EventMaterializedStorageFile.size]. Only exact matches are considered.
     */
    val size: PredicateCollection<Comparison<Long>>? = null
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
data class NumericStatistics(
    val mean: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val sum: Double?,
    val percentiles: List<Double>
)

/**
 * @see [QueryDescriptions.query]
 */
data class QueryRequest(
    override val query: FileQuery,

    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithFileQuery, WithPaginationRequest

/**
 * @see [QueryDescriptions.query]
 */
typealias QueryResponse = Page<EventMaterializedStorageFile>

/**
 * @see [QueryDescriptions.statistics]
 */
data class StatisticsRequest(
    override val query: FileQuery,

    // This is currently very limited. We can figure out what we need later.
    val size: NumericStatisticsRequest? = null,
    val fileDepth: NumericStatisticsRequest? = null
) : WithFileQuery

/**
 * @see [QueryDescriptions.statistics]
 */
data class StatisticsResponse(
    val count: Long,
    val size: NumericStatistics?,
    val fileDepth: NumericStatistics?
)

/**
 * REST interface for queries of indexing data.
 *
 * In general, this can only be accessed by [Roles.PRIVILEDGED] users
 */
object QueryDescriptions : RESTDescriptions("indexing") {
    const val baseContext = "/api/indexing/query"

    val query = callDescription<QueryRequest, QueryResponse, CommonErrorMessage> {
        name = "query"
        method = HttpMethod.Get

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
        }

        body { bindEntireRequestFromBody() }
    }

    val statistics = callDescription<StatisticsRequest, StatisticsResponse, CommonErrorMessage> {
        name = "statistics"
        method = HttpMethod.Get

        auth {
            roles = Roles.PRIVILEDGED
            access = AccessRight.READ
        }

        path {
            using(baseContext)
            +"statistics"
        }

        body { bindEntireRequestFromBody() }
    }
}
