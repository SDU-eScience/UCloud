package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.PredicateCollection
import dk.sdu.cloud.indexing.api.SortDirection
import dk.sdu.cloud.indexing.api.SortRequest
import dk.sdu.cloud.indexing.api.SortableField
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.indexing.util.search
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.PaginationRequest
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.mapItems
import io.ktor.http.HttpStatusCode
import mbuhot.eskotlin.query.compound.bool
import mbuhot.eskotlin.query.fulltext.match_phrase_prefix
import mbuhot.eskotlin.query.term.range
import mbuhot.eskotlin.query.term.terms
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.search.MultiSearchRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchRequestBuilder
import org.elasticsearch.client.ElasticsearchClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.Aggregations
import org.elasticsearch.search.aggregations.metrics.avg.Avg
import org.elasticsearch.search.aggregations.metrics.max.Max
import org.elasticsearch.search.aggregations.metrics.min.Min
import org.elasticsearch.search.aggregations.metrics.percentiles.Percentiles
import org.elasticsearch.search.aggregations.metrics.sum.Sum
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import java.security.Principal

/**
 * An implementation of [IndexQueryService] and [ReverseLookupService] using an Elasticsearch backend.
 */
class ElasticQueryService(
    private val elasticClient: RestHighLevelClient
) : IndexQueryService, ReverseLookupService {
    private val mapper = jacksonObjectMapper()

    override fun findFileByIdOrNull(id: String): EventMaterializedStorageFile? {
        return elasticClient[GetRequest(FILES_INDEX, DOC_TYPE, id)]
            ?.takeIf { it.isExists }
            ?.let { mapper.readValue<ElasticIndexedFile>(it.sourceAsString) }
            ?.toMaterializedFile()
    }

    override fun reverseLookup(fileId: String): String {
        return reverseLookupBatch(listOf(fileId)).single() ?: throw RPCException("Not found", HttpStatusCode.BadRequest)
    }

    override fun reverseLookupBatch(fileIds: List<String>): List<String?> {
        if (fileIds.size > MAX_FILES_IN_REVERSE_BATCH_LOOKUP) throw RPCException(
            "Bad request. Too many file IDs",
            HttpStatusCode.BadRequest
        )

        val req = PaginationRequest(MAX_FILES_IN_REVERSE_BATCH_LOOKUP, 0).normalize()
        val files = elasticClient.search<ElasticIndexedFile>(mapper, req, FILES_INDEX) {
            bool {
                filter {
                    terms { ElasticIndexedFile.ID_FIELD to fileIds }
                }
            }
        }.items.associateBy { it.id }

        return fileIds.map { files[it]?.path }
    }

    override fun query(
        query: FileQuery,
        paging: NormalizedPaginationRequest,
        sorting: SortRequest?
    ): Page<EventMaterializedStorageFile> {
        return elasticClient.search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            if (sorting != null) {
                val field = when (sorting.field) {
                    SortableField.FILE_NAME -> ElasticIndexedFile.FILE_NAME_KEYWORD
                    SortableField.FILE_TYPE -> ElasticIndexedFile.FILE_TYPE_FIELD
                    SortableField.IS_LINK -> ElasticIndexedFile.FILE_IS_LINK_FIELD
                    SortableField.SIZE -> ElasticIndexedFile.SIZE_FIELD
                    SortableField.CREATED_AT -> ElasticIndexedFile.TIMESTAMP_CREATED_FIELD
                    SortableField.MODIFIED_AT -> ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD
                }

                val direction = when (sorting.direction) {
                    SortDirection.ASCENDING -> SortOrder.ASC
                    SortDirection.DESCENDING -> SortOrder.DESC
                }

                sort(field, direction)
            }

            searchBasedOnQuery(query).also {
                log.debug(it.toString())
            }
        }.mapItems { it.toMaterializedFile() }
    }

    private fun multiSearch(users: List<Principal>) {
        val multiSearchRequest = MultiSearchRequest()
        users.forEach {
            multiSearchRequest.add(SearchRequest())
        }

    }

    private fun searchBasedOnQuery(fileQuery: FileQuery): QueryBuilder {
        return with(fileQuery) {
            bool {
                should = ArrayList<QueryBuilder>().apply {
                    fileNameQuery?.forEach { q ->
                        if (!q.isBlank()) {
                            add(match_phrase_prefix {
                                ElasticIndexedFile.FILE_NAME_FIELD to {
                                    this.query = q
                                    max_expansions = FILE_NAME_QUERY_MAX_EXPANSIONS
                                }
                            })
                        }
                    }
                }

                filter = ArrayList<QueryBuilder>().also { list ->
                    val filteredRoots = roots.asSequence().filter { it != "/" }.map { it.removeSuffix("/") }.toList()
                    if (filteredRoots.isNotEmpty()) {
                        list.add(terms { ElasticIndexedFile.PATH_FIELD to filteredRoots })
                    }

                    id.addClausesIfExists(list, ElasticIndexedFile.ID_FIELD)
                    owner.addClausesIfExists(list, ElasticIndexedFile.OWNER_FIELD)
                    fileNameExact.addClausesIfExists(list, ElasticIndexedFile.FILE_NAME_FIELD)
                    extensions.addClausesIfExists(list, ElasticIndexedFile.FILE_NAME_EXTENSION)
                    fileTypes.addClausesIfExists(list, ElasticIndexedFile.FILE_TYPE_FIELD)
                    fileDepth.addClausesIfExists(list, ElasticIndexedFile.FILE_DEPTH_FIELD)
                    createdAt.addClausesIfExists(list, ElasticIndexedFile.TIMESTAMP_CREATED_FIELD)
                    modifiedAt.addClausesIfExists(list, ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD)
                    sensitivity.addClausesIfExists(list, ElasticIndexedFile.SENSITIVITY_FIELD)
                    annotations.addClausesIfExists(list, ElasticIndexedFile.ANNOTATIONS_FIELD)
                    linkTarget.addClausesIfExists(list, ElasticIndexedFile.LINK_TARGET_FIELD)
                    linkTargetId.addClausesIfExists(list, ElasticIndexedFile.LINK_TARGET_ID_FIELD)
                    size.addClausesIfExists(list, ElasticIndexedFile.SIZE_FIELD)

                    if (fileIsLink != null) {
                        list.add(terms { ElasticIndexedFile.FILE_IS_LINK_FIELD to listOf(fileIsLink) })
                    }
                }
            }.also {
                if (it.should().isNotEmpty()) {
                    it.minimumShouldMatch(1)
                }
            }
        }
    }

    private inline fun <reified P : Any> PredicateCollection<P>?.addClausesIfExists(
        list: MutableList<QueryBuilder>,
        fieldName: String
    ) {
        this?.let { list.addAll(it.convertToQuery(fieldName)) }
    }

    private inline fun <reified P : Any> PredicateCollection<P>.convertToQuery(fieldName: String): List<QueryBuilder> {
        val isComparison = P::class == Comparison::class

        return allOf.map {
            if (!isComparison) {
                it.toQuery(fieldName)
            } else {
                @Suppress("UNCHECKED_CAST")
                it as AnyOf<Comparison<*>>

                it.toComparisonQuery(fieldName)
            }
        }
    }

    private fun <P : Any> AnyOf<P>.toQuery(fieldName: String): QueryBuilder {
        val termsQuery = terms { fieldName to anyOf }

        return if (negate) {
            bool {
                must_not = listOf(termsQuery)
            }
        } else {
            termsQuery
        }
    }

    private fun <P : Comparison<*>> AnyOf<P>.toComparisonQuery(fieldName: String): QueryBuilder {
        val equalsTerm = anyOf.find { it.operator == ComparisonOperator.EQUALS }

        val query = if (equalsTerm != null) {
            terms {
                fieldName to listOf(equalsTerm.value)
            }
        } else {
            range {
                fieldName to {
                    anyOf.forEach {
                        when (it.operator) {
                            ComparisonOperator.GREATER_THAN -> gt = it.value
                            ComparisonOperator.GREATER_THAN_EQUALS -> gte = it.value
                            ComparisonOperator.LESS_THAN -> lt = it.value
                            ComparisonOperator.LESS_THAN_EQUALS -> lte = it.value
                            ComparisonOperator.EQUALS -> throw IllegalStateException("Assertion error")
                        }
                    }
                }
            }
        }

        return if (negate) {
            bool {
                must_not = listOf(query)
            }
        } else {
            query
        }
    }

    override fun statisticsQuery(statisticsRequest: StatisticsRequest): StatisticsResponse {
        val result = elasticClient.search(FILES_INDEX) {
            source(SearchSourceBuilder().also { builder ->
                builder.size(0)
                builder.query(searchBasedOnQuery(statisticsRequest.query))

                statisticsRequest.size?.let {
                    addNumericAggregations(builder, it, ElasticIndexedFile.SIZE_FIELD)
                }

                statisticsRequest.fileDepth?.let {
                    addNumericAggregations(builder, it, ElasticIndexedFile.FILE_DEPTH_FIELD)
                }
            })
            println(source().toString())
        }

        val size = statisticsRequest.size?.let {
            retrieveNumericAggregate(result.aggregations, it, ElasticIndexedFile.SIZE_FIELD)
        }

        val fileDepth = statisticsRequest.fileDepth?.let {
            retrieveNumericAggregate(result.aggregations, it, ElasticIndexedFile.FILE_DEPTH_FIELD)
        }

        return StatisticsResponse(
            result.hits.totalHits,
            size,
            fileDepth
        )
    }

    private enum class NumericStat(val variableName: String) {
        MEAN("Mean"),
        MINIMUM("Minimum"),
        MAXIMUM("Maximum"),
        SUM("Sum"),
        PERCENTILES("Percentiles");

        fun computeVariableName(fieldName: String): String {
            return fieldName + variableName
        }
    }

    private fun addNumericAggregations(
        builder: SearchSourceBuilder,
        numericStatisticsRequest: NumericStatisticsRequest,
        fieldName: String
    ) {
        with(builder) {
            if (numericStatisticsRequest.calculateMean) {
                val variableName = NumericStat.MEAN.computeVariableName(fieldName)
                aggregation(
                    AggregationBuilders.avg(variableName).field(fieldName)
                )
            }

            if (numericStatisticsRequest.calculateMinimum) {
                val variableName = NumericStat.MINIMUM.computeVariableName(fieldName)
                aggregation(
                    AggregationBuilders.min(variableName).field(fieldName)
                )
            }

            if (numericStatisticsRequest.calculateMaximum) {
                val variableName = NumericStat.MAXIMUM.computeVariableName(fieldName)
                aggregation(
                    AggregationBuilders.max(variableName).field(fieldName)
                )
            }

            if (numericStatisticsRequest.calculateSum) {
                val variableName = NumericStat.SUM.computeVariableName(fieldName)
                aggregation(
                    AggregationBuilders.sum(variableName).field(fieldName)
                )
            }

            if (numericStatisticsRequest.percentiles.isNotEmpty()) {
                val variableName = NumericStat.PERCENTILES.computeVariableName(fieldName)

                @Suppress("SpreadOperator")
                aggregation(
                    AggregationBuilders
                        .percentiles(variableName)
                        .field(fieldName)
                        .percentiles(*numericStatisticsRequest.percentiles.toDoubleArray())
                )
            }
        }
    }

    private fun retrieveNumericAggregate(
        aggregations: Aggregations,
        numericStatisticsRequest: NumericStatisticsRequest,
        fieldName: String
    ): NumericStatistics {
        val mean = if (numericStatisticsRequest.calculateMean) {
            val variableName = NumericStat.MEAN.computeVariableName(fieldName)
            aggregations.get<Avg>(variableName)!!.value
        } else null

        val min = if (numericStatisticsRequest.calculateMinimum) {
            val variableName = NumericStat.MINIMUM.computeVariableName(fieldName)
            aggregations.get<Min>(variableName)!!.value
        } else null

        val max = if (numericStatisticsRequest.calculateMaximum) {
            val variableName = NumericStat.MAXIMUM.computeVariableName(fieldName)
            aggregations.get<Max>(variableName)!!.value
        } else null

        val sum = if (numericStatisticsRequest.calculateSum) {
            val variableName = NumericStat.SUM.computeVariableName(fieldName)
            aggregations.get<Sum>(variableName)!!.value
        } else null

        val percentiles = if (numericStatisticsRequest.percentiles.isNotEmpty()) {
            val variableName = NumericStat.PERCENTILES.computeVariableName(fieldName)
            val aggregation = aggregations.get<Percentiles>(variableName)!!
            numericStatisticsRequest.percentiles.map { aggregation.percentile(it) }
        } else emptyList()

        return NumericStatistics(
            mean = mean,
            minimum = min,
            maximum = max,
            sum = sum,
            percentiles = percentiles
        )
    }

    companion object : Loggable {
        override val log = logger()

        private const val FILES_INDEX = ElasticIndexingService.FILES_INDEX
        private const val DOC_TYPE = ElasticIndexingService.DOC_TYPE

        private const val MAX_FILES_IN_REVERSE_BATCH_LOOKUP = 100

        private const val FILE_NAME_QUERY_MAX_EXPANSIONS = 10
    }
}
