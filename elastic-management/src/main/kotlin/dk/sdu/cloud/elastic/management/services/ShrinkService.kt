package dk.sdu.cloud.elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest
import org.elasticsearch.action.admin.indices.shrink.ResizeRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings
import org.slf4j.Logger
import java.time.LocalDate

class ShrinkService(
    private val elastic: RestHighLevelClient,
    private val gatherNode: String
) {

    private fun shrinkIndex(sourceIndex: String){
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
        elastic.indices().shrink(request, RequestOptions.DEFAULT)
    }

    private fun prepareSourceIndex(index: String) {
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
        elastic.indices().putSettings(request, RequestOptions.DEFAULT)
    }


    fun shrink() {
        val yesterdayPeriodFormat = LocalDate.now().minusDays(1).toString().replace("-","." )
        val list = elastic.indices().get(GetIndexRequest().indices("*-$yesterdayPeriodFormat"), RequestOptions.DEFAULT).indices

        list.forEach {
            var counter = 0
            log.info("Shrinking $it")
            prepareSourceIndex(it)
            while (elastic.cluster().health(ClusterHealthRequest(it), RequestOptions.DEFAULT).relocatingShards > 0) {
                if (counter % 10 == 0) {
                    log.info("Waiting for relocate")
                }
                counter++
                Thread.sleep(500)
            }
            shrinkIndex(it)
            deleteIndex(it, elastic)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
