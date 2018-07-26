package dk.sdu.cloud.indexing

import dk.sdu.cloud.indexing.services.ElasticIndexingService
import dk.sdu.cloud.indexing.services.ElasticQueryService
import dk.sdu.cloud.indexing.services.InternalSearchResult
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.api.StorageEvent
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import java.util.*

const val recreateIndex = false

fun main(args: Array<String>) {
    val elastic = RestHighLevelClient(RestClient.builder(HttpHost("localhost", 9200, "http")))

    val indexingService = ElasticIndexingService(elastic)
    val queryService = ElasticQueryService(elastic)

    fun MutableList<StorageEvent>.file(path: String, type: FileType) {
        add(
            StorageEvent.CreatedOrModified(
                path,
                path,
                "bob",
                System.currentTimeMillis(),
                type
            )
        )
    }

    fun MutableList<StorageEvent>.rename(path: String, oldPath: String) {
        add(
            StorageEvent.Moved(
                oldPath,
                path,
                "bob",
                System.currentTimeMillis(),
                oldPath
            )
        )
    }

    if (recreateIndex) {
        indexingService.migrate()

        val events = ArrayList<StorageEvent>()


        with(events) {
            repeat(10) { a ->
                file("$a", FileType.DIRECTORY)
                repeat(10) { b ->
                    file("$a/$b", FileType.DIRECTORY)
                    repeat(10) { c ->
                        file("$a/$b/$c", FileType.DIRECTORY)
                        repeat(10) { d ->
                            file("$a/$b/$c/$d", FileType.FILE)
                        }
                    }
                }
            }
        }



        indexingService.bulkHandleEvent(events)
    }

    /*
    val chars = List(10) { ('a' + it) }
    indexingService.bulkHandleEvent(ArrayList<StorageEvent>().apply {
        repeat(10) { aIdx ->
            val a = chars[aIdx]
            rename("$a", "$aIdx")
            repeat(10) { bIdx ->
                val b = chars[bIdx]
                rename("$a/$b", "$aIdx/$bIdx")
                repeat(10) { cIdx ->
                    val c = chars[cIdx]
                    rename("$a/$b/$c", "$aIdx/$bIdx/$cIdx")
                    repeat(10) { dIdx ->
                        val d = chars[dIdx]
                        rename("$a/$b/$c/$d", "$aIdx/$bIdx/$cIdx/$dIdx")
                    }
                }
            }
        }
    })
    */

    var currentPage = 0
    var itemsInTotal = 1

    val results = ArrayList<InternalSearchResult>()
    while (currentPage * 100 < itemsInTotal) {
        val page = queryService.simpleQuery(
            listOf("a", "c/d"),
            "e",
            NormalizedPaginationRequest(100, currentPage)
        )

        results.addAll(page.items)

        itemsInTotal = page.itemsInTotal
        currentPage++
    }

    results.sortedBy { it.path }.forEach { println(it) }

    elastic.close()
}