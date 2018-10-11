package dk.sdu.cloud.indexing.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.file.api.EventMaterializedStorageFile
import dk.sdu.cloud.file.api.FileType
import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.filesearch.api.TimestampQuery
import dk.sdu.cloud.indexing.api.AnyOf
import dk.sdu.cloud.indexing.api.Comparison
import dk.sdu.cloud.indexing.api.ComparisonOperator
import dk.sdu.cloud.indexing.api.FileQuery
import dk.sdu.cloud.indexing.api.NumericStatistics
import dk.sdu.cloud.indexing.api.NumericStatisticsRequest
import dk.sdu.cloud.indexing.api.PredicateCollection
import dk.sdu.cloud.indexing.api.StatisticsRequest
import dk.sdu.cloud.indexing.api.StatisticsResponse
import dk.sdu.cloud.indexing.util.isNullOrEmpty
import dk.sdu.cloud.indexing.util.search
import dk.sdu.cloud.indexing.util.term
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
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.Aggregations
import org.elasticsearch.search.aggregations.metrics.avg.Avg
import org.elasticsearch.search.aggregations.metrics.max.Max
import org.elasticsearch.search.aggregations.metrics.min.Min
import org.elasticsearch.search.aggregations.metrics.percentiles.PercentileRanks
import org.elasticsearch.search.aggregations.metrics.sum.Sum
import org.elasticsearch.search.builder.SearchSourceBuilder

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

    override fun simpleQuery(
        roots: List<String>,
        query: String,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> = elasticClient
        .search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            sort(ElasticIndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = listOf(
                    match_phrase_prefix {
                        ElasticIndexedFile.FILE_NAME_FIELD to {
                            this.query = query
                            max_expansions = 10
                        }
                    },

                    term {
                        boost = 0.5f
                        ElasticIndexedFile.OWNER_FIELD to query
                    }
                )

                filter {
                    terms {
                        ElasticIndexedFile.PATH_FIELD to roots
                    }
                }

                // minimum_should_match = 1
                // TODO Can't use this. eskotlin is compiled against an old version which isn't binary compatible
                // Also seems like eskotlin development is mostly dead, we should fork it.
            }.also {
                it.minimumShouldMatch(1)
            }
        }
        .mapItems { it.toMaterializedFile() }

    override fun advancedQuery(
        roots: List<String>,
        name: String?,
        owner: String?,
        extensions: List<String>?,
        fileTypes: List<FileType>?,
        createdAt: TimestampQuery?,
        modifiedAt: TimestampQuery?,
        sensitivity: List<SensitivityLevel>?,
        annotations: List<String>?,
        paging: NormalizedPaginationRequest
    ): Page<EventMaterializedStorageFile> {
        if (name == null && owner == null && fileTypes.isNullOrEmpty() && createdAt == null &&
            modifiedAt == null && sensitivity.isNullOrEmpty() && annotations.isNullOrEmpty() &&
            extensions.isNullOrEmpty()
        ) {
            return Page(0, paging.itemsPerPage, paging.page, emptyList())
        }

        return elasticClient.search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            sort(ElasticIndexedFile.FILE_NAME_KEYWORD)

            bool {
                should = ArrayList<QueryBuilder>().apply {
                    if (name != null) {
                        add(match_phrase_prefix {
                            ElasticIndexedFile.FILE_NAME_FIELD to {
                                query = name
                                max_expansions = 10
                            }
                        })
                    }
                }

                filter = ArrayList<QueryBuilder>().apply {
                    add(terms { ElasticIndexedFile.PATH_FIELD to roots })

                    if (createdAt != null) {
                        add(range {
                            ElasticIndexedFile.TIMESTAMP_CREATED_FIELD to {
                                if (createdAt.after != null) gte = createdAt.after
                                if (createdAt.before != null) lte = createdAt.before
                            }
                        })
                    }

                    if (modifiedAt != null) {
                        add(range {
                            ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD to {
                                if (modifiedAt.after != null) gte = modifiedAt.after
                                if (modifiedAt.before != null) lte = modifiedAt.before
                            }
                        })
                    }

                    if (owner != null) {
                        add(term {
                            ElasticIndexedFile.OWNER_FIELD to owner
                        })
                    }

                    if (!sensitivity.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.SENSITIVITY_FIELD to sensitivity!!.map { it.name }
                        })
                    }

                    if (!annotations.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.ANNOTATIONS_FIELD to annotations!!
                        })
                    }

                    if (!extensions.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.FILE_NAME_EXTENSION to extensions!!
                        })
                    }

                    if (!fileTypes.isNullOrEmpty()) {
                        add(terms {
                            ElasticIndexedFile.FILE_TYPE_FIELD to fileTypes!!.map { it.name }
                        })
                    }
                }
            }.also {
                // If only the name query is supplied it should at least match the should entry.
                if (it.filter().isEmpty() && it.should().isNotEmpty()) {
                    it.minimumShouldMatch(1)
                }
            }
        }.mapItems { it.toMaterializedFile() }
    }

    override fun reverseLookup(fileId: String): String {
        return reverseLookupBatch(listOf(fileId)).single() ?: throw RPCException("Not found", HttpStatusCode.BadRequest)
    }

    override fun reverseLookupBatch(fileIds: List<String>): List<String?> {
        if (fileIds.size > 100) throw RPCException("Bad request. Too many file IDs", HttpStatusCode.BadRequest)

        val req = PaginationRequest(100, 0).normalize()
        val files = elasticClient.search<ElasticIndexedFile>(mapper, req, FILES_INDEX) {
            bool {
                filter {
                    terms { ElasticIndexedFile.ID_FIELD to fileIds }
                }
            }
        }.items.associateBy { it.id }

        return fileIds.map { files[it]?.path }
    }

    override fun newQuery(query: FileQuery, paging: NormalizedPaginationRequest): Page<EventMaterializedStorageFile> {
        return elasticClient.search<ElasticIndexedFile>(mapper, paging, FILES_INDEX) {
            searchBasedOnQuery(query)
        }.mapItems { it.toMaterializedFile() }
    }

    private fun searchBasedOnQuery(fileQuery: FileQuery): QueryBuilder {
        return with(fileQuery) {
            bool {
                should = ArrayList<QueryBuilder>().apply {
                    if (fileNameQuery != null) {
                        add(match_phrase_prefix {
                            ElasticIndexedFile.FILE_NAME_FIELD to {
                                this.query = fileNameQuery
                                max_expansions = 10
                            }
                        })
                    }
                }

                filter = ArrayList<QueryBuilder>().also { list ->
                    list.add(terms { ElasticIndexedFile.PATH_FIELD to roots })

                    id.addClauses(list, ElasticIndexedFile.ID_FIELD)
                    owner.addClauses(list, ElasticIndexedFile.OWNER_FIELD)
                    fileNameExact.addClauses(list, ElasticIndexedFile.FILE_NAME_FIELD)
                    extensions.addClauses(list, ElasticIndexedFile.FILE_NAME_EXTENSION)
                    fileTypes.addClauses(list, ElasticIndexedFile.FILE_TYPE_FIELD)
                    fileDepth.addClauses(list, ElasticIndexedFile.FILE_DEPTH_FIELD)
                    createdAt.addClauses(list, ElasticIndexedFile.TIMESTAMP_CREATED_FIELD)
                    modifiedAt.addClauses(list, ElasticIndexedFile.TIMESTAMP_MODIFIED_FIELD)
                    sensitivity.addClauses(list, ElasticIndexedFile.SENSITIVITY_FIELD)
                    annotations.addClauses(list, ElasticIndexedFile.ANNOTATIONS_FIELD)
                    linkTarget.addClauses(list, ElasticIndexedFile.LINK_TARGET_FIELD)
                    linkTargetId.addClauses(list, ElasticIndexedFile.LINK_TARGET_ID_FIELD)
                    size.addClauses(list, ElasticIndexedFile.SIZE_FIELD)

                    if (fileIsLink != null) {
                        list.add(terms { ElasticIndexedFile.FILE_IS_LINK_FIELD to listOf(fileIsLink) })
                    }
                }
            }
        }
    }

    private inline fun <reified P : Any> PredicateCollection<P>?.addClauses(
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
        val rangeQuery = range {
            fieldName to {
                anyOf.forEach {
                    when (it.operator) {
                        ComparisonOperator.GREATER_THAN -> gt = it.value
                        ComparisonOperator.GREATER_THAN_EQUALS -> gte = it.value
                        ComparisonOperator.LESS_THAN -> lt = it.value
                        ComparisonOperator.LESS_THAN_EQUALS -> lte = it.value
                    }
                }
            }
        }

        return if (negate) {
            bool {
                must_not = listOf(rangeQuery)
            }
        } else {
            rangeQuery
        }
    }

    override fun newStatistics(statisticsRequest: StatisticsRequest): StatisticsResponse {
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
        }

        val size = statisticsRequest.size?.let {
            retrieveNumericAggregate(result.aggregations, it, ElasticIndexedFile.SIZE_FIELD)
        }

        val fileDepth = statisticsRequest.size?.let {
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
            val aggregation = aggregations.get<PercentileRanks>(variableName)!!
            numericStatisticsRequest.percentiles.map { aggregation.percent(it) }
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
    }
}
