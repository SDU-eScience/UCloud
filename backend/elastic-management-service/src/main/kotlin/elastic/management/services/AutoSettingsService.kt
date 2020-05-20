package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.action.admin.cluster.settings.ClusterUpdateSettingsRequest
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.PutIndexTemplateRequest
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
            .put("cluster.routing.allocation.disk.watermark.low", "100GB")
            //high = Elasticsearch tries to relocate shards away from a node
            // if it has less than the specified amount of fee space available
            .put("cluster.routing.allocation.disk.watermark.high", "50GB")
            //flood_stage = Elasticsearch will shutdown all indices that have shards located
            // on nodes with less than the specified amount of free space available
            // Sets the indicies to read/delete only. Should be manually changed using the
            // "removeFloodLimitation" function below.
            .put("cluster.routing.allocation.disk.watermark.flood_stage", "25GB")

        watermarkUpdateRequest.persistentSettings(settings)
        elastic.cluster().putSettings(watermarkUpdateRequest, RequestOptions.DEFAULT)
    }

    private fun createLoggingTemplates() {
        //create template for development_default and http_logs
        val developmentTemplateRequest =
            PutIndexTemplateRequest("development-template")
                .patterns(listOf("development_default*"))
                .settings(Settings.builder()
                    .put("index.number_of_shards", 1)
                    .put("index.number_of_replicas", 1)
                )

        elastic.indices().putTemplate(developmentTemplateRequest, RequestOptions.DEFAULT)

        val productionTemplateRequest =
            PutIndexTemplateRequest("production-template")
                .patterns(listOf("kubernetes-production*"))
                .settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .put("index.refresh_interval", "30s")
                )

        elastic.indices().putTemplate(productionTemplateRequest, RequestOptions.DEFAULT)

        val httpTemplateRequest =
            PutIndexTemplateRequest("httplogs-template")
                .patterns(listOf("http_logs_*"))
                .settings(Settings.builder()
                    .put("index.number_of_shards", 2)
                    .put("index.number_of_replicas", 2)
                )

        elastic.indices().putTemplate(httpTemplateRequest, RequestOptions.DEFAULT)

        val filebeatTemplate =
            PutIndexTemplateRequest("filebeat-template")
                .patterns(listOf("filebeat*"))
                .settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .put("index.refresh_interval", "30s")
                )

        elastic.indices().putTemplate(filebeatTemplate, RequestOptions.DEFAULT)

        val infrastructureTemplate =
            PutIndexTemplateRequest("infrastructure-template")
                .patterns(listOf("infrastructure*"))
                .settings(Settings.builder()
                    .put("index.number_of_shards", 3)
                    .put("index.number_of_replicas", 1)
                    .put("index.refresh_interval", "30s")
                )

        elastic.indices().putTemplate(infrastructureTemplate, RequestOptions.DEFAULT)
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
