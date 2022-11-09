package dk.sdu.cloud.extract.data.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.extract.data.api.UCloudUser
import dk.sdu.cloud.service.db.async.DBContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval
import org.elasticsearch.search.aggregations.metrics.Cardinality
import org.elasticsearch.search.builder.SearchSourceBuilder
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

class ElasticDataService(
    val elasticHighLevelClient: RestHighLevelClient,
    val elasticLowLevelClient: RestClient,
    val db: DBContext
) {
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
            val timeBetween = Duration.between(user.createdAt, lastRequestTime).toMinutes()
            timeBetweenStartAndNewest += timeBetween
        }
        println("average time:")
        println(timeBetweenStartAndNewest / numberOfUsers)
    }

    @Serializable
    data class Principal(
        val username: String,
        val role: String
    )

    @Serializable
    data class Token(
        val principal: Principal
    )

    @Serializable
    data class DownloadRequest(
        val path: String
    )

    @Serializable
    data class CreateDownloadRequest(
        val id: String
    )

    @Serializable
    data class RequestJsonDownload(
        val request: DownloadRequest
    )

    @Serializable
    data class RequestJsonCreateDownload(
        val items: List<CreateDownloadRequest>
    )

    @Serializable
    data class Resources(
        val path: String
    )

    @Serializable
    data class CreateJobRequest(
        val resources: List<Resources?>,
        val openedFile: String? = null
    )

    @Serializable
    data class RequestJsonJobCreate(
        val items: List<CreateJobRequest>
    )

    @Serializable
    data class Download(
        @SerialName("@timestamp")
        val timestamp: String,
        val token: Token?,
        val requestName: String,
        val requestJson: RequestJsonDownload
    )

    @Serializable
    data class CreateDownload(
        @SerialName("@timestamp")
        val timestamp: String,
        val token: Token?,
        val requestName: String,
        val requestJson: RequestJsonCreateDownload
    )

    @Serializable
    data class JobCreate(
        @SerialName("@timestamp")
        val timestamp: String,
        val token: Token?,
        val requestName: String,
        val requestJson: RequestJsonJobCreate
    )

    fun downloadsForProject(projectIds: List<String>) {
        data class FileCollection(
            val id: Long,
            val providerGenId: String?,
            val projectId: String,
            val title: String
        )

        val fileCollections = emptyList<FileCollection>()//TODO() GET RELEVANT FILECOLLECTIONS MANUALLY

        val searchRequest1 = SearchRequest("http_logs_files.download*")
        searchRequest1.source(
            SearchSourceBuilder()
                .query(
                    QueryBuilders
                        .boolQuery()
                        .should(
                            QueryBuilders.boolQuery().apply {
                                projectIds.forEach {
                                    should().add(
                                        QueryBuilders.matchPhraseQuery(
                                            "requestJson.request.path",
                                            "/projects/${it}*"
                                        )
                                    )
                                }
                                fileCollections.forEach {
                                    should().add(
                                        QueryBuilders.matchPhraseQuery(
                                            "requestJson.request.path",
                                            "/${it.id}*"
                                        )
                                    )
                                }
                            }
                        ).minimumShouldMatch(1)
                ).size(10000)
        )

        val searchResponse1 = elasticHighLevelClient.search(searchRequest1, RequestOptions.DEFAULT)
        if (searchResponse1.hits.totalHits!!.value > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results1 = searchResponse1.hits.hits.mapNotNull { hit ->
            val download = defaultMapper.decodeFromString<Download>(hit.sourceAsString)
            if (download.token == null) {
                null
            } else {
                download
            }
        }

        val searchRequest2 = SearchRequest("http_logs_files.createdownload*")
        searchRequest2.source(
            SearchSourceBuilder()
                .query(
                    QueryBuilders
                        .boolQuery()
                        .should(
                            QueryBuilders.boolQuery().apply {
                                projectIds.forEach {
                                    should().add(QueryBuilders.queryStringQuery("\\/projects\\/${it}*"))
                                }
                                fileCollections.forEach {
                                    should().add(QueryBuilders.queryStringQuery("\\/${it.id}*"))
                                }
                            }
                        ).minimumShouldMatch(1)
                ).size(10000)
        )

        val searchResponse2 = elasticHighLevelClient.search(searchRequest2, RequestOptions.DEFAULT)
        if (searchResponse2.hits.totalHits!!.value > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results2 = searchResponse2.hits.hits.mapNotNull { hit ->
            val download = defaultMapper.decodeFromString<CreateDownload>(hit.sourceAsString)
            if (download.token == null) {
                null
            } else {
                download
            }
        }

        val searchRequest3 = SearchRequest("http_logs_jobs.create*")
        searchRequest3.source(
            SearchSourceBuilder()
                .query(
                    QueryBuilders
                        .boolQuery()
                        .should(
                            QueryBuilders.boolQuery().apply {
                                projectIds.forEach {
                                    should().add(QueryBuilders.queryStringQuery("\\/projects\\/${it}*"))
                                }
                                fileCollections.forEach {
                                    should().add(QueryBuilders.queryStringQuery("\\/${it.id}*"))
                                }
                            }
                        ).minimumShouldMatch(1)
                ).size(10000)
        )

        val searchResponse3 = elasticHighLevelClient.search(searchRequest3, RequestOptions.DEFAULT)
        if (searchResponse3.hits.totalHits!!.value > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results3 = searchResponse3.hits.hits.mapNotNull { hit ->
            val jobstart = defaultMapper.decodeFromString<JobCreate>(hit.sourceAsString)
            if (jobstart.token == null) {
                null
            } else {
                jobstart
            }
        }

        data class Data(
            val username: String,
            val requestName: String,
            val paths: List<String>,
            val timestamp: String
        )

        val downloadData = results1.map {
            Data(
                it.token!!.principal.username,
                it.requestName,
                listOf(replacePath(it.requestJson.request.path)!!),
                it.timestamp
            )
        }

        val createDownloadData = results2.map {
            Data(
                it.token!!.principal.username,
                it.requestName,
                it.requestJson.items.map { path -> replacePath(path.id)!! },
                it.timestamp
            )
        }

        val createJobData = results3.map {
            Data(
                it.token!!.principal.username,
                it.requestName,
                it.requestJson.items.mapNotNull { path -> replacePath(path.openedFile) } + joinResources(it.requestJson.items.map { resources -> resources.resources }),
                it.timestamp
            )
        }

        val allData = downloadData + createDownloadData + createJobData
        File("Filepath").writeText(allData.joinToString(separator = "") { it.toString() + "\n" })

    }


    private fun replacePath(path: String?): String? {
        if (path == null) {
            return null
        }
        val splittedPath = path.split("/")
        if (splittedPath[1] == "projects") {
            val realtitle = when (splittedPath[2]) {
                //TODO rename projectIDs to project title
                "ProjectID" -> "Project Title"
                else -> throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
            }
            val realpath = splittedPath.toMutableList()
            realpath[2] = realtitle
            return realpath.joinToString("/")
        } else {
            val realtitle = when (splittedPath[1]) {
                //TODO()Rename all collection IDs to readable path
                "FilecollectionID" -> "projects/projectTitle/FilecollectionTitle"
                else -> {
                    println(splittedPath)
                    throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)
                }
            }
            val realpath = splittedPath.toMutableList()
            realpath[1] = realtitle
            return realpath.joinToString("/")
        }
    }

    private fun joinResources(list: List<List<Resources?>>): List<String> {
        val returnList = ArrayList<String>()
        list.forEach { resources ->
            resources.forEach {
                if (it != null) {
                    returnList.add(replacePath(it.path)!!)
                }
            }
        }
        return returnList.toList()
    }
}

