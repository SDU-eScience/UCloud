package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import mbuhot.eskotlin.query.term.exists
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest
import org.elasticsearch.action.admin.indices.shrink.ResizeRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.ResponseException
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings
import org.slf4j.Logger
import java.io.IOException
import java.time.LocalDate

class ShrinkService(
    private val elastic: RestHighLevelClient,
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
        } while (elastic.cluster().health(ClusterHealthRequest(index), RequestOptions.DEFAULT).relocatingShards > 0)
    }

    private fun shrinkIndex(sourceIndex: String){
        var retries = 0
        while (retries < 3) {
            val targetIndex = sourceIndex + "_small"
            val request = ResizeRequest(targetIndex, sourceIndex)
            request.targetIndexRequest.settings(
                Settings.builder()
                    //Set number of shards in new shrinked index.
                    //Should always be a factor the original. 15 -> 5,3,1.
                    .put("index.number_of_shards", 1)
                    //Makes sure that the new index is writable
                    .put("index.blocks.write", false)
                    //Set the number of replicas in the new shrinked index.
                    .put("index.number_of_replicas", 1)
                    //Choose that the index should use best_compression strategy.
                    //Slower search, but less space usage
                    .put("index.codec", "best_compression")
            )
            try {
                elastic.indices().shrink(request, RequestOptions.DEFAULT)
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
                    is ElasticsearchStatusException -> {
                        deleteIndex(targetIndex, elastic)
                    }
                    else -> throw Exception("Another error has occured: ${ex.message}")
                }
                retries++
                continue
            }
            mergeIndex(elastic, targetIndex)
            return
        }
        throw Exception("Too many retries on shrink of $sourceIndex")
    }

    private fun prepareSourceIndex(index: String) {
        var retries = 0
        while (retries < 3) {
            //What node should the shards be collected on before shrink is performed
            val setNodeSettingKey = "index.routing.allocation.require._name"
            val setNodeSettingValue = gatherNode

            //Make sure that no more is being written to the index. Block writing.
            val setBlockSettingKey = "index.blocks.write"
            val setBlockSettingValue = true

            val request = UpdateSettingsRequest(index)

            val settings =
                Settings.builder()
                    .put(setNodeSettingKey, setNodeSettingValue)
                    .put(setBlockSettingKey, setBlockSettingValue)
                    .build()

            request.settings(settings)
            try {
                elastic.indices().putSettings(request, RequestOptions.DEFAULT)
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
            prepareSourceIndex(it)
            waitForRelocation(it)
            shrinkIndex(it)
            deleteIndex(it, elastic)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
