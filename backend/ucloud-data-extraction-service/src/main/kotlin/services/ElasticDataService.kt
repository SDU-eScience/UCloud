package dk.sdu.cloud.ucloud.data.extraction.services

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.ucloud.data.extraction.api.UCloudUser
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.joda.time.LocalDateTime
import org.joda.time.Minutes
import java.io.File


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
        val tree = defaultMapper.readTree(searchResponse.toString())
        val mostConcurrent = tree["aggregations"]["date_histogram#requests_per_15_min"]["buckets"].maxByOrNull { it ->
            it["sterms#users"]["buckets"].filter { !it["key"].textValue().startsWith("_") }.size
        } ?: error("No max")
        val users = mostConcurrent["sterms#users"]["buckets"].filter {
            !it["key"].textValue().startsWith("_")
        }.map { it["key"] }
        println(users)
        println(users.size)
    }

    fun avarageUserActivity() {

    }

    fun activityPeriod(users: List<UCloudUser>) {
        val numberOfUsers = users.size

        var timeBetweenStartAndNewest = 0L
        users.forEach { user ->
            println(user)
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
            val tree = defaultMapper.readTree(result.toString())
            val lastRequestTime = LocalDateTime.parse(tree["_source"]["@timestamp"].textValue().substringBefore("Z"))
            val timeBetween = Minutes.minutesBetween(user.createdAt, lastRequestTime).minutes
            timeBetweenStartAndNewest += timeBetween
        }
        println("average time:")
        println(timeBetweenStartAndNewest/numberOfUsers)
    }

}
