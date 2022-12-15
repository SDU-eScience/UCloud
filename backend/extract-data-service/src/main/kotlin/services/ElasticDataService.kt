package dk.sdu.cloud.extract.data.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Time
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation
import co.elastic.clients.elasticsearch._types.aggregations.CardinalityAggregation
import co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregation
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchPhraseQuery
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery
import co.elastic.clients.elasticsearch._types.query_dsl.QueryStringQuery
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery
import co.elastic.clients.elasticsearch._types.query_dsl.WildcardQuery
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.json.JsonData
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dk.sdu.cloud.calls.CallDescription
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.extract.data.api.UCloudUser
import dk.sdu.cloud.service.db.async.DBContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval
import java.io.File
import java.time.Duration
import java.time.LocalDateTime

class ElasticDataService(
    val elasticHighLevelClient: ElasticsearchClient,
    val db: DBContext
) {
    fun maxSimultaneousUsers(startDate: LocalDateTime, endDate: LocalDateTime) {
        val searchRequest = SearchRequest.Builder()
            .index("http_logs*")
            .query(
                MatchAllQuery.Builder().build()._toQuery()
            )
            .query(
                RangeQuery.Builder()
                    .field("@timestamp")
                    .from(startDate.toString())
                    .to(endDate.toString())
                    .build()._toQuery()
            )
            .aggregations(
                "requests_per_15_min",
                Aggregation(
                    DateHistogramAggregation.Builder()
                        .field("@timestamp")
                        .fixedInterval(Time.Builder().time("15m").build())
                        .build()
                )
            )
            .build()

//TODO() SUB AGGREGATION
       /*         .aggregation(
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
        )*/
        val searchResponse = elasticHighLevelClient.search(searchRequest, CallDescription::class.java)
        val tree = jacksonObjectMapper().readTree(searchResponse.toString())
        val mostConcurrent = tree["aggregations"]["date_histogram#requests_per_15_min"]["buckets"].maxByOrNull { it ->
            it["sterms#users"]["buckets"].filter { !it["key"].textValue().startsWith("_") }.size
        } ?: error("No max")
        val users = mostConcurrent["sterms#users"]["buckets"].filter {
            !it["key"].textValue().startsWith("_")
        }.map { it["key"] }
    }

    fun activeUsers(start: Long, end: Long): Long {
        val searchRequest = SearchRequest.Builder()
            .index("http_logs*")
            .query(
                BoolQuery.Builder()
                    .must(
                        WildcardQuery.Builder()
                            .field("token.principal.username.keyword")
                            .value("*")
                            .build()._toQuery()
                    )
                    .mustNot(
                        WildcardQuery.Builder()
                            .field("token.principal.role")
                            .value("SERVICE")
                            .build()._toQuery()
                    )
                    .filter(
                        RangeQuery.Builder()
                            .field("@timestamp")
                            .gte(JsonData.of(start))
                            .lte(JsonData.of(end))
                            .build()._toQuery()
                    )
                    .build()._toQuery()
            )
            .aggregations(
                "UserCount",
                CardinalityAggregation.Builder().field("token.principal.username.keyword").build()._toAggregation()
            )
            .build()
        val searchResponse = elasticHighLevelClient.search(searchRequest, CallDescription::class.java)
        return searchResponse.aggregations()["UserCount"]?.cardinality()?.value() ?: 0L
    }

    fun activityPeriod(users: List<UCloudUser>) {
        val numberOfUsers = users.size

        var timeBetweenStartAndNewest = 0L
        users.forEach { user ->
            val searchRequest = SearchRequest.Builder()
                .index("http_logs*")
                .query(
                    MatchQuery.Builder()
                        .field( "token.principal.username.keyword")
                        .query(user.username)
                        .build()._toQuery()
                )
                .size(1)
                .build()
            val searchResponse = elasticHighLevelClient.search(searchRequest, CallDescription::class.java)
            val result = searchResponse.hits().hits().firstOrNull() ?: return@forEach
            val lastRequestTime = LocalDateTime.parse(result.fields()["@timestamp"].toString().substringBefore("Z"))
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

        val searchRequest1 = SearchRequest.Builder()
            .index("http_logs_files.download*")
            .query(
                BoolQuery.Builder()
                    .should(
                        BoolQuery.Builder().apply {
                            projectIds.forEach {
                                this.should(
                                    MatchPhraseQuery.Builder()
                                        .field("requestJson.request.path")
                                        .query("/projects/${it}*")
                                        .build()._toQuery()
                                )
                            }
                            fileCollections.forEach {
                                this.should(
                                    MatchPhraseQuery.Builder()
                                        .field("requestJson.request.path")
                                        .query("/${it.id}*")
                                        .build()._toQuery()
                                )
                            }
                        }.build()._toQuery()
                    )
                    .minimumShouldMatch("1")
                    .build()._toQuery()
            )
            .size(10000)
            .build()

        val searchResponse1 = elasticHighLevelClient.search(searchRequest1, Download::class.java)
        if (searchResponse1.hits().total()!!.value() > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results1 = searchResponse1.hits().hits().mapNotNull { hit ->
            val download = hit.source()
            if (download?.token == null) {
                null
            } else {
                download
            }
        }

        val searchRequest2 = SearchRequest.Builder()
            .index("http_logs_files.createdownload*")
            .query(
                BoolQuery.Builder()
                    .should(
                        BoolQuery.Builder().apply {
                            projectIds.forEach {
                                this.should(
                                    QueryStringQuery.Builder().query("\\/projects\\/${it}*").build()._toQuery()
                                )
                            }
                            fileCollections.forEach {
                                this.should(
                                    QueryStringQuery.Builder().query("\\/${it.id}*").build()._toQuery()
                                )
                            }
                        }.build()._toQuery()
                    )
                    .minimumShouldMatch("1")
                    .build()._toQuery()
            ).size(10000)
            .build()

        val searchResponse2 = elasticHighLevelClient.search(searchRequest2, CreateDownload::class.java)
        if (searchResponse2.hits().total()!!.value() > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results2 = searchResponse2.hits().hits().mapNotNull { hit ->
            val download = hit.source()
            if (download?.token == null) {
                null
            } else {
                download
            }
        }

        val searchRequest3 = SearchRequest.Builder()
            .index("http_logs_jobs.create*")
            .query(
                BoolQuery.Builder()
                    .should(
                        BoolQuery.Builder().apply {
                            projectIds.forEach {
                                this.should(
                                    QueryStringQuery.Builder().query("\\/projects\\/${it}*").build()._toQuery()
                                )
                            }
                            fileCollections.forEach {
                                this.should(
                                    QueryStringQuery.Builder().query("\\/${it.id}*").build()._toQuery()
                                )
                            }
                        }.build()._toQuery()
                    )
                    .minimumShouldMatch("1")
                    .build()._toQuery()
            ).size(10000)
            .build()

        val searchResponse3 = elasticHighLevelClient.search(searchRequest3, JobCreate::class.java)
        if (searchResponse3.hits().total()!!.value() > 10000L) {
            println("MORE HITS THAN LIMIT")
        }
        val results3 = searchResponse3.hits().hits().mapNotNull { hit ->
            val jobstart = hit.source()
            if (jobstart?.token == null) {
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

