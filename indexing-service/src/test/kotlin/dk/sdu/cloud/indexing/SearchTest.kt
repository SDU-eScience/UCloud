package dk.sdu.cloud.indexing

import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.service.NormalizedPaginationRequest
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient

fun main(args: Array<String>) {
    val elastic = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))
    val query = ElasticQueryService(elastic)

    val firstPage = query.simpleQuery(
        listOf("/home/jonas@hinchely.dk"),
        "new",
        NormalizedPaginationRequest(null, null)
    )

    println(firstPage)
}