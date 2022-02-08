package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.extract.data.api.UCloudUser
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval
import org.elasticsearch.search.aggregations.metrics.Cardinality
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.joda.time.LocalDateTime
import org.joda.time.Minutes


class ElasticDataService(val elasticHighLevelClient: RestHighLevelClient, val elasticLowLevelClient: RestClient) {

    fun maxSimultaneousUsers(startDate: LocalDateTime, endDate: LocalDateTime) {
        val searchRequest = SearchRequest("http_logs*")
        searchRequest.source(
            SearchSourceBuilder()
                .query(
                    QueryBuilders.matchAllQuery()
                )
                .query(
                    QueryBuilders.rangeQuery("@timestamp")
                        .from(startDate.toString())
                        .to(endDate.toString())
                )
                .aggregation(
                    AggregationBuilders
                        .dateHistogram("requests_per_15_min")
                        .field("@timestamp")
                        .fixedInterval(DateHistogramInterval("15m"))
                        .subAggregation(
                            AggregationBuilders
                                .terms("users")
                                .field("token.principal.username.keyword")
                        )
                )
        )
        val searchResponse = elasticHighLevelClient.search(searchRequest, RequestOptions.DEFAULT)
        val tree = jacksonObjectMapper().readTree(searchResponse.toString())
        val mostConcurrent = tree["aggregations"]["date_histogram#requests_per_15_min"]["buckets"].maxByOrNull { it ->
            it["sterms#users"]["buckets"].filter { !it["key"].textValue().startsWith("_") }.size
        } ?: error("No max")
        val users = mostConcurrent["sterms#users"]["buckets"].filter {
            !it["key"].textValue().startsWith("_")
        }.map { it["key"] }
    }

    fun activeUsers(start: Long, end: Long): Long {
        val searchRequest = SearchRequest("http_logs*")
        searchRequest.source(
            SearchSourceBuilder()
                .query(
                    QueryBuilders
                        .boolQuery()
                        .must(
                            QueryBuilders.wildcardQuery(
                                "token.principal.username.keyword",
                                "*"
                            )
                        )
                        .mustNot(
                            QueryBuilders.wildcardQuery(
                                "token.principal.role",
                                "SERVICE"
                            )
                        )
                        .filter(
                            QueryBuilders.rangeQuery("@timestamp")
                                .gte(start)
                                .lte(end)
                        )
                ).aggregation(
                    AggregationBuilders
                        .cardinality("UserCount")
                        .field("token.principal.username.keyword")
                )


        )
        val searchResponse = elasticHighLevelClient.search(searchRequest, RequestOptions.DEFAULT)
        return searchResponse.aggregations.get<Cardinality>("UserCount").value
    }

    fun activityPeriod(users: List<UCloudUser>) {
        val numberOfUsers = users.size

        var timeBetweenStartAndNewest = 0L
        users.forEach { user ->
            val searchRequest = SearchRequest("http_logs*")
            searchRequest.source(
                SearchSourceBuilder()
                    .query(
                        QueryBuilders
                            .matchQuery(
                                "token.principal.username.keyword",
                                user.username
                            )
                    )
                    .size(1)
            )
            val searchResponse = elasticHighLevelClient.search(searchRequest, RequestOptions.DEFAULT)
            val result = searchResponse.hits.hits.firstOrNull() ?: return@forEach
            val tree = jacksonObjectMapper().readTree(result.toString())
            val lastRequestTime = LocalDateTime.parse(tree["_source"]["@timestamp"].textValue().substringBefore("Z"))
            val timeBetween = Minutes.minutesBetween(user.createdAt, lastRequestTime).minutes
            timeBetweenStartAndNewest += timeBetween
        }
        println("average time:")
        println(timeBetweenStartAndNewest/numberOfUsers)
    }

}

