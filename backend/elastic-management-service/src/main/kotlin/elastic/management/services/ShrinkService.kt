package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.ElasticsearchException
import co.elastic.clients.elasticsearch.cluster.HealthRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.elasticsearch.indices.ShrinkRequest
import co.elastic.clients.json.JsonData
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.client.Request
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestClient
import org.slf4j.Logger
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate

class ShrinkService(
    private val elastic: ElasticsearchClient,
    private val lowlevelClient: RestClient,
    private val gatherNode: String
) {

    private fun waitForRelocation(index: String) {
        var counter = 0
        do {
            if (counter % 10 == 0) {
                log.info("Waiting for relocate")
            }
            counter++
            Thread.sleep(1000)
        } while (elastic.cluster().health(
                HealthRequest.Builder()
                    .index(index)
                    .build()
            ).relocatingShards() > 0)
    }

    private fun shrinkIndex(sourceIndex: String){
        var retries = 0
        while (retries < 3) {
            val targetIndex = sourceIndex + "_small"
            val request = ShrinkRequest.Builder()
                .index(sourceIndex)
                .target(targetIndex)
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        //Set number of shards in new shrinked index.
                        //Should always be a factor the original. 15 -> 5,3,1.
                        put("index.number_of_shards", JsonData.of(1))
                        //Makes sure that the new index is writable
                        put("index.blocks.write", JsonData.of(false))
                        //Set the number of replicas in the new shrinked index.
                        put("index.number_of_replicas", JsonData.of(1))
                        //Choose that the index should use best_compression strategy.
                        //Slower search, but less space usage
                        put("index.codec", JsonData.of("best_compression"))
                        //Should have access to all nodes again
                        put("index.routing.allocation.require._name", JsonData.of("*"))
                    }
                )
                .build()
            try {
                elastic.indices().shrink(request)
            } catch (ex: Exception) {
                when (ex) {
                    //handeling internal error on "allocation should be on one node"
                    is ResponseException -> {
                        if (ex.response.statusLine.statusCode == 500) {
                            waitForRelocation(sourceIndex)
                        }
                    }
                    //usually an index-already-exists error due to previous failure having created the resized
                    // index but failed before it could delete the original index
                    is ElasticsearchException -> {
                        log.warn(ex.stackTraceToString())
                        //If both exists
                        if (elastic.indices().exists(ExistsRequest.Builder().index(sourceIndex).build()).value()
                            && elastic.indices().exists(ExistsRequest.Builder().index(targetIndex).build()).value()) {
                            log.info("Both Indices exists: $targetIndex, $sourceIndex")
                            if (isSameSize(sourceIndex, targetIndex, elastic)) {
                                log.info("They are same size")
                                return
                            } else {
                                deleteIndex(targetIndex, elastic)
                            }
                        }
                    }
                    else -> throw Exception("Another error has occured: ${ex.message}")
                }
                retries++
                continue
            }
            try {
                mergeIndex(elastic, targetIndex)
            } catch (ex: Exception) {
                when (ex) {
                    is SocketTimeoutException -> {
                        log.info("Caught TimeoutException - It is okay - merge still happening")
                        //giving elastic time to finish some task that might be the result of Timeout
                        Thread.sleep(2000)
                    }
                    else -> {
                        log.info("Caught other exception - should still be okay but:")
                        ex.printStackTrace()
                    }
                }
            }
            return
        }
        throw Exception("Too many retries on shrink of $sourceIndex")
    }

    private fun prepareSourceIndex(index: String ) {
        var retries = 0
        while (retries < 3) {
            //What node should the shards be collected on before shrink is performed
            //"index.routing.allocation.require._name"
            // gatherNode

            //Make sure that no more is being written to the index. Block writing.
            //"index.blocks.write"
            //true

            val r = Request("PUT", "/$index/_settings")

            r.setJsonEntity("""
                {
                    "index.routing.allocation.require._name":"$gatherNode",
                    "index.blocks.write": true
                }
                """.trimIndent())

            try {
                lowlevelClient.performRequest(r)
                return
            } catch (ex: IOException) {
                log.info("IOException - retrying")
                retries++
            }
        }
        throw IOException("Too many retries while setting settings")
    }


    fun shrink() {
        val yesterdayPeriodFormat = LocalDate.now().minusDays(1).toString().replace("-","." )
        val list = getListOfIndices(elastic, "*-$yesterdayPeriodFormat")
        log.info("Shrinking ${list.size} indices")
        list.forEach {
            log.info("Shrinking $it")
            if (getShardCount(it, elastic) == 1) {
                log.info("Index is already at 1 shard")
                return@forEach
            }
            prepareSourceIndex(it)
            waitForRelocation(it)
            shrinkIndex(it)
            if (isSameSize(it, it+"_small", elastic)) {
                deleteIndex(it, elastic)
            }
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
