package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.cluster.PutClusterSettingsRequest
import co.elastic.clients.elasticsearch.indices.IndexSettingBlocks
import co.elastic.clients.elasticsearch.indices.IndexSettings
import co.elastic.clients.elasticsearch.indices.IndexTemplate
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest
import co.elastic.clients.elasticsearch.indices.PutIndicesSettingsRequest
import co.elastic.clients.elasticsearch.indices.PutTemplateRequest
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping
import co.elastic.clients.json.JsonData
import dk.sdu.cloud.defaultMapper
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.xcontent.XContentType

class AutoSettingsService(
    private val elastic: ElasticsearchClient
) {

    fun setup() {
        setWaterMarkLevels()
        createLoggingTemplates()
    }

    private fun setWaterMarkLevels() {
        val watermarkUpdateRequest = PutClusterSettingsRequest.Builder()
            .persistent(
                mutableMapOf<String?, JsonData?>().apply {
                    //low = prevents ElasticSearch from allocation shards if less
                    // than the specified amount of space is available
                    put("cluster.routing.allocation.disk.watermark.low", JsonData.of("100GB"))
                    //high = Elasticsearch tries to relocate shards away from a node
                    // if it has less than the specified amount of fee space available
                    put("cluster.routing.allocation.disk.watermark.high", JsonData.of("50GB"))
                    //flood_stage = Elasticsearch will shutdown all indices that have shards located
                    // on nodes with less than the specified amount of free space available
                    // Sets the indicies to read/delete only. Should be manually changed using the
                    // "removeFloodLimitation" function below.
                    put("cluster.routing.allocation.disk.watermark.flood_stage", JsonData.of("25GB"))                }
            )
            .build()

        elastic.cluster().putSettings(watermarkUpdateRequest)
    }

    private fun createLoggingTemplates() {
        //create template for development_default and http_logs
        val developmentTemplateRequest =
            PutTemplateRequest.Builder()
                .name("development-template")
                .indexPatterns(listOf("development_default*"))
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        put("index.number_of_shards", JsonData.of(1))
                        put("index.number_of_replicas", JsonData.of(1))
                    }
                )
                .build()

        elastic.indices().putTemplate(developmentTemplateRequest)

        val productionTemplateRequest =
            PutTemplateRequest.Builder()
                .name("production-template")
                .indexPatterns(listOf("kubernetes-production*"))
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        put("index.number_of_shards", JsonData.of(3))
                        put("index.number_of_replicas", JsonData.of(1))
                        put("index.refresh_interval", JsonData.of("30s"))
                    }
                )
                .build()

        elastic.indices().putTemplate(productionTemplateRequest)

        val httpTemplateRequest =
            PutTemplateRequest.Builder()
                .name("httplogs-template")
                .indexPatterns(listOf("http_logs_*"))
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        put("index.number_of_shards", JsonData.of(2))
                        put("index.number_of_replicas", JsonData.of(2))
                    }
                )
                .build()

        elastic.indices().putTemplate(httpTemplateRequest)

        val filebeatTemplate =
            PutTemplateRequest.Builder()
                .name("filebeat-template")
                .indexPatterns(listOf("filebeat*"))
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        put("index.number_of_shards", JsonData.of(3))
                        put("index.number_of_replicas", JsonData.of(1))
                        put("index.refresh_interval", JsonData.of("30s"))
                    }
                )
                .build()

        elastic.indices().putTemplate(filebeatTemplate)

        val infrastructureTemplate =
            PutTemplateRequest.Builder()
                .name("infrastructure-template")
                .indexPatterns(listOf("infrastructure*"))
                .settings(
                    mutableMapOf<String?, JsonData?>().apply {
                        put("index.number_of_shards", JsonData.of(3))
                        put("index.number_of_replicas", JsonData.of(1))
                        put("index.refresh_interval", JsonData.of("30s"))
                    }
                )
                .build()

        elastic.indices().putTemplate(infrastructureTemplate)
    }

    fun removeFloodLimitationOnAll() {
        removeFloodLimitation("*")
    }

    fun removeFloodLimitation(index: String) {
        val updateSettingsRequest = PutIndicesSettingsRequest.Builder()
            .settings(
                IndexSettings.Builder()
                    .blocks(
                        IndexSettingBlocks.Builder()
                            .readOnlyAllowDelete(null)
                            .build()
                    )
                    .build()
            )
            .build()

        elastic.indices().putSettings(updateSettingsRequest)
        
    }

}
