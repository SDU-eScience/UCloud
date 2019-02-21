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
    private val elastic: RestHighLevelClient
) {

    fun deleteIndex(index: String) {
        val request = DeleteIndexRequest(index)
        elastic.indices().delete(request, RequestOptions.DEFAULT)
    }

    private fun shrinkIndex(sourceIndex: String){
        val targetIndex = sourceIndex + "_small"
        val request = ResizeRequest(targetIndex, sourceIndex)
        request.targetIndexRequest.settings(
            Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.blocks.write", false)
                .put("index.number_of_replicas", 1)
                .put("index.codec", "best_compression")
        )
        elastic.indices().shrink(request, RequestOptions.DEFAULT)
    }

    private fun prepareSourceIndex(index: String) {
        val setNodeSettingKey = "index.routing.allocation.require._name"
        val setNodeSettingValue = "elasticsearch-data-0"

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
        val yesterday = LocalDate.now().minusDays(1).toString().replace("-","." )
        val list = elastic.indices().get(GetIndexRequest().indices("*-$yesterday"), RequestOptions.DEFAULT).indices

        list.forEach {
            prepareSourceIndex(it)
        }

        val indices = list.joinToString().replace("\\s".toRegex(), "")
        log.debug("Working on $indices")
        while (elastic.cluster().health(ClusterHealthRequest(indices), RequestOptions.DEFAULT).relocatingShards > 0) {
            log.info("Waiting for relocate")
            Thread.sleep(500)
        }

        list.forEach {
            shrinkIndex(it)
            deleteIndex(it)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }
}
