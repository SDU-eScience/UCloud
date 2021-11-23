package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.file.ucloud.api.*
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.NormalizedPaginationRequestV2
import dk.sdu.cloud.service.PageV2
import kotlinx.serialization.decodeFromString
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
import org.elasticsearch.search.aggregations.metrics.*
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import services.FileScanner

class ElasticQueryService(
    private val elasticClient: RestHighLevelClient,
    private val fastDirectoryStats: CephFsFastDirectoryStats?,
    private val cephFsRoot: String
) {
    private val mapper = defaultMapper

    fun queryForScan(
        query: FileQuery,
    ): List<ElasticIndexedFile> {
        val searchRequest = SearchRequest(FILES_INDEX)
        val source = SearchSourceBuilder()

        source.query(
            with(query) {
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
                            fileDepth.addClausesIfExists(this, ElasticIndexedFileConstants.FILE_DEPTH_FIELD)
                        }
                        if (should().isNotEmpty()) {
                            minimumShouldMatch(1)
                        }
                    }
                }
            }
        )
        source.size(10000)
        searchRequest.source(source)
        searchRequest.scroll(TimeValue.timeValueMinutes(1))

        var response = elasticClient.search(searchRequest, RequestOptions.DEFAULT)
        var scrollId = response.scrollId
        var hits = response.hits.hits

        val elasticFiles = mutableListOf<ElasticIndexedFile>()

        while (hits != null && hits.size > 0) {
            hits.map {
                elasticFiles.add(mapper.decodeFromString(it.sourceAsString))
            }

            val searchScrollRequest = SearchScrollRequest(scrollId)
            searchScrollRequest.scroll(TimeValue.timeValueMinutes(1))
            response = elasticClient.scroll(searchScrollRequest, RequestOptions.DEFAULT)
            scrollId = response.scrollId
            hits = response.hits.hits
        }

        val clearScrollRequest = ClearScrollRequest()
        clearScrollRequest.addScrollId(scrollId)
        elasticClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT)

        return elasticFiles
    }

    fun query(
        query: FileQuery,
        paging: NormalizedPaginationRequestV2,
        sorting: SortRequest? = null
    ): PageV2<ElasticIndexedFile> {
        if (paging.next != null ) {
            val searchScrollRequest = SearchScrollRequest(paging.next)
            searchScrollRequest.scroll(TimeValue.timeValueMinutes(1))
            val response = elasticClient.scroll(searchScrollRequest, RequestOptions.DEFAULT)
            var scrollId = response.scrollId
            val hits = response.hits.hits.map {
                mapper.decodeFromString<ElasticIndexedFile>(it.sourceAsString)
            }

            if (hits.isEmpty()) {
                scrollId = null
            }
            return PageV2(paging.itemsPerPage, hits, scrollId)
        }

        val request = SearchRequest()
        val source = SearchSourceBuilder()
        source.query(searchBasedOnQuery(query))

        if (sorting != null) {
            val field = when (sorting.field) {
                SortableField.FILE_NAME -> ElasticIndexedFileConstants.FILE_NAME_KEYWORD
                SortableField.FILE_TYPE -> ElasticIndexedFileConstants.FILE_TYPE_FIELD
                SortableField.SIZE -> ElasticIndexedFileConstants.SIZE_FIELD
            }

            val direction = when (sorting.direction) {
                SortDirection.ASCENDING -> SortOrder.ASC
                SortDirection.DESCENDING -> SortOrder.DESC
            }

            source.sort(field, direction)
        }

        source.size(paging.itemsPerPage)
        request.scroll(TimeValue.timeValueMinutes(1))

        val response = elasticClient.search(request, RequestOptions.DEFAULT)
        val scrollId = response.scrollId
        val hits = response.hits.hits.map {
            mapper.decodeFromString<ElasticIndexedFile>(it.sourceAsString)
        }

        return PageV2(paging.itemsPerPage, hits , scrollId)
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

        private const val FILE_NAME_QUERY_MAX_EXPANSIONS = 10
    }
}
