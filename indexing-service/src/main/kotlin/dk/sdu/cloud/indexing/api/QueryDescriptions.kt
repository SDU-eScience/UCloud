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

typealias PredicateCollection<Pred> = AllOf<Pred>

data class AllOf<Pred : Any>(val allOf: List<AnyOf<Pred>>) {
    init {
        if (allOf.isEmpty()) throw IllegalArgumentException("allOf cannot be empty")
    }
}

data class AnyOf<Pred : Any>(val anyOf: List<Pred>, val negate: Boolean) {
    init {
        if (anyOf.isEmpty()) throw IllegalArgumentException("anyOf cannot be empty")
    }
}

enum class ComparisonOperator {
    GREATER_THAN,
    GREATER_THAN_EQUALS,
    LESS_THAN,
    LESS_THAN_EQUALS
}

data class Comparison<Value>(val value: Value, val operator: ComparisonOperator)

/**
 * A query for files. It is used in various request classes.
 */
data class FileQuery(
    val roots: List<String>,

    val id: PredicateCollection<String>? = null,
    val owner: PredicateCollection<String>? = null,

    val fileNameQuery: List<String>? = null,

    val fileNameExact: PredicateCollection<String>? = null,
    val extensions: PredicateCollection<String>? = null,
    val fileTypes: PredicateCollection<FileType>? = null,
    val fileDepth: PredicateCollection<Comparison<Int>>? = null,

    val createdAt: PredicateCollection<Comparison<Long>>? = null,
    val modifiedAt: PredicateCollection<Comparison<Long>>? = null,

    val sensitivity: PredicateCollection<SensitivityLevel>? = null,
    val annotations: PredicateCollection<String>? = null,

    val fileIsLink: Boolean? = null,
    val linkTarget: PredicateCollection<String>? = null,
    val linkTargetId: PredicateCollection<String>? = null,

    val size: PredicateCollection<Comparison<Long>>? = null
) {
    init {
        if (roots.isEmpty()) throw IllegalArgumentException("roots cannot be empty")
    }
}

interface WithFileQuery {
    val query: FileQuery
}

data class NumericStatisticsRequest(
    val calculateMean: Boolean = false,
    val calculateMinimum: Boolean = false,
    val calculateMaximum: Boolean = false,
    val calculateSum: Boolean = false,
    val percentiles: List<Double> = emptyList()
)

data class NumericStatistics(
    val mean: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val sum: Double?,
    val percentiles: List<Double>
)

data class QueryRequest(
    override val query: FileQuery,

    override val itemsPerPage: Int? = null,
    override val page: Int? = null
) : WithFileQuery, WithPaginationRequest

typealias QueryResponse = Page<EventMaterializedStorageFile>

data class StatisticsRequest(
    override val query: FileQuery,

    // This is currently very limited. We can figure out what we need later.
    val size: NumericStatisticsRequest? = null,
    val fileDepth: NumericStatisticsRequest? = null
) : WithFileQuery

data class StatisticsResponse(
    val count: Long,
    val size: NumericStatistics?,
    val fileDepth: NumericStatistics?
)

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
