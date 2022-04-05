package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.orchestrator.api.FilesProviderSearchRequest
import dk.sdu.cloud.file.orchestrator.api.PartialUFile
import dk.sdu.cloud.file.ucloud.api.AnyOf
import dk.sdu.cloud.file.ucloud.api.Comparison
import dk.sdu.cloud.file.ucloud.api.ComparisonOperator
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFile
import dk.sdu.cloud.file.ucloud.api.ElasticIndexedFileConstants
import dk.sdu.cloud.file.ucloud.api.FileQuery
import dk.sdu.cloud.file.ucloud.api.NumericStatistics
import dk.sdu.cloud.file.ucloud.api.NumericStatisticsRequest
import dk.sdu.cloud.file.ucloud.api.PredicateCollection
import dk.sdu.cloud.file.ucloud.api.StatisticsRequest
import dk.sdu.cloud.file.ucloud.api.StatisticsResponse
import dk.sdu.cloud.file.ucloud.api.toPartialUFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.PageV2
import kotlinx.serialization.decodeFromString
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.search.ClearScrollRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.core.TimeValue
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.query.RangeQueryBuilder
import org.elasticsearch.index.query.TermsQueryBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.Aggregations
import org.elasticsearch.search.aggregations.metrics.Avg
import org.elasticsearch.search.aggregations.metrics.Max
import org.elasticsearch.search.aggregations.metrics.Min
import org.elasticsearch.search.aggregations.metrics.Percentiles
import org.elasticsearch.search.aggregations.metrics.Sum
import org.elasticsearch.search.aggregations.metrics.ValueCount
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder

class ElasticQueryService(
    private val elasticClient: RestHighLevelClient,
    private val nativeFS: NativeFS,
    private val pathConverter: PathConverter
) {
    private val mapper = defaultMapper

    private suspend fun verifyAndUpdateIndex(hits: List<PartialUFile>): List<PartialUFile> {
        //making sure that the results does not contain duplicates
        val filteredHits = hits.toSet()

        val illegalIndices = mutableListOf<PartialUFile>()

        val existingHits = filteredHits.mapNotNull { partialUFile ->
            try {
                nativeFS.stat(pathConverter.ucloudToInternal(UCloudFile.createFromPreNormalizedString(partialUFile.id)))
                partialUFile
            } catch (ex: Exception) {
                if (ex is FSException.NotFound) {
                    log.warn("Path not found")
                    illegalIndices.add(partialUFile)
                    null
                } else {
                    throw ex
                }
            }
        }

        // Deletes indices that do not exist anymore
        val bulk = BulkRequest()
        illegalIndices.forEach {
            bulk.add(DeleteRequest(FILES_INDEX, it.id))
        }
        if (illegalIndices.isNotEmpty()) {
            log.info("Deleting: $illegalIndices from ES (does not exist anymore)")
            elasticClient.bulk(bulk, RequestOptions.DEFAULT)
        }
        return existingHits
    }

    suspend fun query(
        searchRequest: FilesProviderSearchRequest
    ): PageV2<PartialUFile> {
        val normalizedRequest = searchRequest.normalize()
        if (searchRequest.query.isBlank()) {
            return PageV2(normalizedRequest.itemsPerPage, emptyList(), null)
        }
        if (searchRequest.next != null) {
            val searchScrollRequest = SearchScrollRequest(searchRequest.next)
            searchScrollRequest.scroll(TimeValue.timeValueMinutes(1))
            val response = elasticClient.scroll(searchScrollRequest, RequestOptions.DEFAULT)
            var scrollId = response.scrollId
            val hits = response.hits.hits.map {
                mapper.decodeFromString<ElasticIndexedFile>(it.sourceAsString).toPartialUFile()
            }

            if (hits.isEmpty()) {
                scrollId = null
            }

            val existingHits = verifyAndUpdateIndex(hits)

            return PageV2(normalizedRequest.itemsPerPage, existingHits, scrollId)
        }

        val request = SearchRequest(FILES_INDEX)
        val source = SearchSourceBuilder()

        source.query(searchBasedOnQuery(searchRequest))

        source.sort(ElasticIndexedFileConstants.FILE_NAME_KEYWORD, SortOrder.ASC)

        source.size(normalizedRequest.itemsPerPage)
        request.source(source)
        request.scroll(TimeValue.timeValueMinutes(1))

        val response = try {
            elasticClient.search(request, RequestOptions.DEFAULT)
        } catch (ex: Exception) {
            throw ex
        }
        val scrollId = response.scrollId
        val hits = response.hits.hits.map {
            mapper.decodeFromString<ElasticIndexedFile>(it.sourceAsString).toPartialUFile()
        }

        val existingHits = verifyAndUpdateIndex(hits)

        return PageV2(
            normalizedRequest.itemsPerPage,
            existingHits,
            if ((response.hits?.totalHits?.value ?: 0) > normalizedRequest.itemsPerPage) scrollId else null
        )
    }

    private fun searchBasedOnQuery(searchRequest: FilesProviderSearchRequest): QueryBuilder {
        val isNumber = try {
            searchRequest.query.toLong()
            true
        } catch (ex: NumberFormatException) {
            false
        }

        return with(searchRequest) {
            QueryBuilders.boolQuery().apply {
                should().apply {
                    add(
                        QueryBuilders.matchPhrasePrefixQuery(
                            ElasticIndexedFileConstants.FILE_NAME_FIELD,
                            query
                        ).maxExpansions(FILE_NAME_QUERY_MAX_EXPANSIONS)
                    )
                    if (isNumber) {
                        add(
                            QueryBuilders.matchQuery(
                                ElasticIndexedFileConstants.CREATED_AT_FIELD,
                                query
                            )
                        )
                        add(
                            QueryBuilders.matchQuery(
                                ElasticIndexedFileConstants.SIZE_FIELD,
                                query
                            )
                        )
                    }
                }
                filter().apply {
                    if (searchRequest.owner.project == null) {
                        add(
                            TermsQueryBuilder(
                                ElasticIndexedFileConstants.OWNER_FIELD,
                                searchRequest.owner.createdBy
                            )
                        )
                    } else {
                        add(TermsQueryBuilder(ElasticIndexedFileConstants.PROJECT_ID, searchRequest.owner.project))
                    }
                    if (flags.filterByFileExtension != null) {
                        add(
                            TermsQueryBuilder(
                                ElasticIndexedFileConstants.FILE_NAME_EXTENSION,
                                flags.filterByFileExtension
                            )
                        )
                    }
                }
                if (should().isNotEmpty()) {
                    minimumShouldMatch(1)
                }
            }
        }
    }

    private fun searchBasedOnQuery(fileQuery: FileQuery): QueryBuilder {
        return with(fileQuery) {
            QueryBuilders.boolQuery().apply {
                should().apply {
                    fileNameQuery?.forEach { q ->
                        if (!q.isBlank()) {
                            add(
                                QueryBuilders.matchPhrasePrefixQuery(
                                    ElasticIndexedFileConstants.FILE_NAME_FIELD,
                                    q
                                ).maxExpansions(FILE_NAME_QUERY_MAX_EXPANSIONS)
                            )
                        }
                    }
                    filter().apply {
                        val filteredRoots =
                            roots.asSequence().filter { it != "/" }.map { it.removeSuffix("/") }.toList()
                        if (filteredRoots.isNotEmpty()) {
                            add(TermsQueryBuilder(ElasticIndexedFileConstants.PATH_FIELD, filteredRoots))
                        }
                        fileNameExact.addClausesIfExists(this, ElasticIndexedFileConstants.FILE_NAME_FIELD)
                        extensions.addClausesIfExists(this, ElasticIndexedFileConstants.FILE_NAME_EXTENSION)
                        fileTypes.addClausesIfExists(this, ElasticIndexedFileConstants.FILE_TYPE_FIELD)
                        fileDepth.addClausesIfExists(this, ElasticIndexedFileConstants.FILE_DEPTH_FIELD)
                        fileSize.addClausesIfExists(this, ElasticIndexedFileConstants.SIZE_FIELD)
                    }
                    if (should().isNotEmpty()) {
                        minimumShouldMatch(1)
                    }
                }
            }
        }
    }

    fun statisticsQuery(statisticsRequest: StatisticsRequest): StatisticsResponse {
        val searchRequest = SearchRequest(FILES_INDEX)
        val source = SearchSourceBuilder()
        source.also { builder ->
            builder.size(0)
            builder.query(searchBasedOnQuery(statisticsRequest.query))

            builder.aggregation(
                AggregationBuilders.count("completeCount").field(ElasticIndexedFileConstants.FILE_NAME_KEYWORD)
            )

            statisticsRequest.size?.let {
                addNumericAggregations(builder, it, ElasticIndexedFileConstants.SIZE_FIELD)
            }

            statisticsRequest.fileDepth?.let {
                addNumericAggregations(builder, it, ElasticIndexedFileConstants.FILE_DEPTH_FIELD)
            }
        }

        searchRequest.source(source)

        val result = elasticClient.search(searchRequest, RequestOptions.DEFAULT)

        val size = statisticsRequest.size?.let {
            retrieveNumericAggregate(result.aggregations, it, ElasticIndexedFileConstants.SIZE_FIELD)
        }

        val fileDepth = statisticsRequest.fileDepth?.let {
            retrieveNumericAggregate(result.aggregations, it, ElasticIndexedFileConstants.FILE_DEPTH_FIELD)
        }

        return StatisticsResponse(
            runCatching { result.aggregations.get<ValueCount>("completeCount").value }.getOrDefault(0L),
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

    private fun <P : Comparison<*>> AnyOf<P>.toComparisonQuery(fieldName: String): QueryBuilder {
        val equalsTerm = anyOf.find { it.operator == ComparisonOperator.EQUALS }

        val query = if (equalsTerm != null) {
            TermsQueryBuilder(
                fieldName, listOf(equalsTerm.value)
            )
        } else {
            RangeQueryBuilder(fieldName).apply {
                anyOf.forEach {
                    when (it.operator) {
                        ComparisonOperator.GREATER_THAN -> gt(it.value)
                        ComparisonOperator.GREATER_THAN_EQUALS -> gte(it.value)
                        ComparisonOperator.LESS_THAN -> lt(it.value)
                        ComparisonOperator.LESS_THAN_EQUALS -> lte(it.value)
                        ComparisonOperator.EQUALS -> throw IllegalStateException("Assertion error")
                    }
                }
            }
        }

        return if (negate) {
            QueryBuilders.boolQuery().mustNot(query)
        } else {
            query
        }
    }

    private fun <P : Any> AnyOf<P>.toQuery(fieldName: String): QueryBuilder {
        val termsQuery = TermsQueryBuilder(fieldName, anyOf.map { it.toString() })

        return if (negate) {
            QueryBuilders.boolQuery().mustNot(termsQuery)
        } else {
            termsQuery
        }
    }

    companion object : Loggable {
        override val log = logger()

        private const val FILES_INDEX = FileScanner.FILES_INDEX

        private const val FILE_NAME_QUERY_MAX_EXPANSIONS = 50
    }
}
