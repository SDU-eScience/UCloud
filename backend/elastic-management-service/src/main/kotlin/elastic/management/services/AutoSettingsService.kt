package dk.sdu.cloud.elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.Time
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
import org.elasticsearch.cluster.metadata.ComposableIndexTemplate
import org.elasticsearch.cluster.metadata.Template
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
                    put("cluster.routing.allocation.disk.watermark.flood_stage", JsonData.of("25GB"))
                }
            )
            .build()

        elastic.cluster().putSettings(watermarkUpdateRequest)
    }

    private fun createLoggingTemplates() {
        //create template for development_default and http_logs
        val developmentTemplateRequest =
            PutIndexTemplateRequest.Builder()
                .name("development-template")
                .indexPatterns(listOf("development_default*"))
                .template(
                    IndexTemplateMapping.Builder()
                        .settings(
                            IndexSettings.Builder()
                                .numberOfShards("1")
                                .numberOfReplicas("1")
                                .build()
                        )
                        .build()
                )
                .build()

        elastic.indices().putIndexTemplate(developmentTemplateRequest)

        val productionTemplateRequest =
            PutIndexTemplateRequest.Builder()
                .name("production-template")
                .indexPatterns(listOf("kubernetes-production*"))
                .template(
                    IndexTemplateMapping.Builder()
                        .settings(
                            IndexSettings.Builder()
                                .numberOfShards("3")
                                .numberOfReplicas("1")
                                .refreshInterval(Time.Builder().time("30s").build())
                                .build()
                        )
                        .build()
                )
                .build()

        elastic.indices().putIndexTemplate(productionTemplateRequest)

        val httpTemplateRequest =
            PutIndexTemplateRequest.Builder()
                .name("httplogs-template")
                .indexPatterns(listOf("http_logs_*"))
                .template(
                    IndexTemplateMapping.Builder()
                        .settings(
                            IndexSettings.Builder()
                                .numberOfShards("1")
                                .numberOfReplicas("1")
                                .build()
                        )
                        .build()
                )
                .build()

        elastic.indices().putIndexTemplate(httpTemplateRequest)

        val filebeatTemplate =
            PutIndexTemplateRequest.Builder()
                .name("filebeat-template")
                .indexPatterns(listOf("filebeat*"))
                .template(
                    IndexTemplateMapping.Builder()
                        .settings(
                            IndexSettings.Builder()
                                .numberOfShards("3")
                                .numberOfReplicas("1")
                                .refreshInterval(Time.Builder().time("30s").build())
                                .build()
                        )
                        .build()
                )
                .build()

        elastic.indices().putIndexTemplate(filebeatTemplate)

        val infrastructureTemplate =
            PutIndexTemplateRequest.Builder()
                .name("infrastructure-template")
                .indexPatterns(listOf("infrastructure*"))
                .template(
                    IndexTemplateMapping.Builder()
                        .settings(
                            IndexSettings.Builder()
                                .numberOfShards("3")
                                .numberOfReplicas("1")
                                .refreshInterval(Time.Builder().time("30s").build())
                                .build()
                        )
                        .build()
                )
                .build()

        elastic.indices().putIndexTemplate(infrastructureTemplate)
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
