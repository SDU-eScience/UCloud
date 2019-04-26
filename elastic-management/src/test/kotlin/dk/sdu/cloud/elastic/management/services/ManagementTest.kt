package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.elastic.management.ElasticHostAndPort
import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.flush.FlushRequest
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType
import org.junit.Ignore
import org.junit.Test
import java.time.LocalDate
import java.util.*


class ManagementTest {

    private val node = ""
    private val mount = "path/to/something"

    private val elastic = RestHighLevelClient(
        RestClient.builder(
            HttpHost(
                "localhost",
                9200,
                "http"
            )
        )
    )

    private fun createDocuments(
        index: String,
        numberOfDaysBeforeNow: Long,
        numberOfDocuments: Int,
        expired: Boolean = true
    ): String{
        val date = Date().time
        val pastdate = LocalDate.now().minusDays(numberOfDaysBeforeNow).toString().replace("-","." )

        var bulkRequest = BulkRequest()
        for (i in 0 until numberOfDocuments) {
            val request = IndexRequest("$index-${pastdate}_small", "doc")
            val jsonString = """
                {
                    "user":"kimchy",
                    "postDate": "$i",
                    "message": "This is message $i!",
                    "expiry": ${ if (expired) date-22222 else date+date}
                }
            """.trimIndent()
            request.source(jsonString, XContentType.JSON)
            bulkRequest.add(request)
            if (i%10000 == 0) {
                elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
                bulkRequest = BulkRequest()
            }
        }

        elastic.bulk(bulkRequest, RequestOptions.DEFAULT)
        return "$index-$pastdate"
    }

    @Ignore
    @Test
    fun `Multiple index find and delete test`() {
        val startCreateTime = Date().time
        var indices = "${createDocuments("hello", 0, 50000)},"
        indices += "${createDocuments("hello", 1, 50000)},"
        indices += "${createDocuments("hello", 2, 50000)},"
        indices += "${createDocuments("hello", 3, 50000)},"
        indices += createDocuments("hello", 4, 50000)
        val endCreateTime = Date().time
        println("Time to create: ${endCreateTime-startCreateTime} millsec")

        elastic.indices().flush(FlushRequest(indices), RequestOptions.DEFAULT)

        val deleteService = ExpiredEntriesDeleteService(elastic)

        val starttime = Date().time
        deleteService.deleteExpiredAllIndices()
        val endtime = Date().time

        println("Took: ${endtime-starttime} millsec")
    }

    @Ignore
    @Test
    fun `Multiple index shrink test`() {
        var indices = "${createDocuments("hello", 1, 100000)},"
        indices += "${createDocuments("mojn", 1, 100000)},"
        indices += "${createDocuments("hi", 1, 100000)},"
        indices += "${createDocuments("goddag", 2, 100000)},"
        indices += createDocuments("dav", 1, 100000)
        println("Created")

        elastic.indices().flush(FlushRequest(indices), RequestOptions.DEFAULT)

        val shrinkService = ShrinkService(elastic, node)

        shrinkService.shrink()

    }

    @Ignore
    @Test
    fun `backup test`() {
        val backupService = BackupService(elastic, mount)
        backupService.start()
        backupService.deleteBackup()
    }

    @Ignore
    @Test
    fun `test Settings`() {
        val service = AutoSettingsService(elastic)
        service.removeFloodLimitationOnAll()
    }

    @Ignore
    @Test
    fun `test reindex`() {
        createDocuments("http_logs_mojn", 8, 500)
        createDocuments("http_logs_mojn", 9, 500)
        createDocuments("http_logs_mojn", 11, 500)
        createDocuments("http_logs_mojn", 12, 500)
        createDocuments("http_logs_mojn", 13, 500)

        createDocuments("http_logs_activity", 8, 500)
        createDocuments("http_logs_activity", 9, 500)
        createDocuments("http_logs_activity", 11, 500)
        createDocuments("http_logs_activity", 12, 500)
        createDocuments("http_logs_activity", 13, 500)

        elastic.indices().flush(FlushRequest("*"), RequestOptions.DEFAULT)

        val service = ReindexService(elastic)
        service.reindexLogsWithPrefixAWeekBackFrom(7, "http_logs", ElasticHostAndPort("localhost"))
    }

    @Ignore
    @Test
    fun `getAllTest`() {
        println(getAllLogNamesWithPrefix(elastic, "http_logs"))
    }

    @Test
    fun `Delete all empty test`() {
        val service = ExpiredEntriesDeleteService(elastic)
        service.deleteAllEmptyIndices()
    }
}
