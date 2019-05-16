package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType

class AutoSettingsService(
    private val elastic: RestHighLevelClient
) {

    fun setup() {
        setWaterMarkLevels()
        createLoggingTemplates()
    }

    private fun setWaterMarkLevels() {
        val watermarkUpdateRequest = ClusterUpdateSettingsRequest()

        val settings = Settings.builder()
            //low = prevents ElasticSearch from allocation shards if less
            // than the specified amount of space is available
            .put("cluster.routing.allocation.disk.watermark.low", "50GB")
            //high = Elasticsearch tries to relocate shards away from a node
            // if it has less than the specified amount of fee space available
            .put("cluster.routing.allocation.disk.watermark.high", "25GB")
            //flood_stage = Elasticsearch will shutdown all indices that have shards located
            // on nodes with less than the specified amount of free space available
            // Sets the indicies to read/delete only. Should be manually changed using the
            // "removeFloodLimitation" function below.
            .put("cluster.routing.allocation.disk.watermark.flood_stage", "10GB")

        watermarkUpdateRequest.persistentSettings(settings)
        elastic.cluster().putSettings(watermarkUpdateRequest, RequestOptions.DEFAULT)
    }

    private fun createLoggingTemplates() {
        //create template for development_default and http_logs
        val developmentTemplateRequest = PutIndexTemplateRequest("development-template")
        developmentTemplateRequest.patterns(listOf("development_default*"))

        developmentTemplateRequest.settings(Settings.builder()
            .put("index.number_of_shards", 1)
            .put("index.number_of_replicas", 1)
        )

        elastic.indices().putTemplate(developmentTemplateRequest, RequestOptions.DEFAULT)


        val httpTemplateRequest = PutIndexTemplateRequest("httplogs-template")
        httpTemplateRequest.patterns(listOf("http_logs_*"))

        httpTemplateRequest.settings(Settings.builder()
            .put("index.number_of_shards", 2)
            .put("index.number_of_replicas", 1)
        )

        elastic.indices().putTemplate(httpTemplateRequest, RequestOptions.DEFAULT)
    }

    fun removeFloodLimitationOnAll() {
        removeFloodLimitation("*")
    }

    fun removeFloodLimitation(index: String) {
        val updateSettingsRequest = UpdateSettingsRequest(index)
        updateSettingsRequest.settings("""{"index.blocks.read_only_allow_delete" : null}""", XContentType.JSON)

        elastic.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT)
        
    }

}
