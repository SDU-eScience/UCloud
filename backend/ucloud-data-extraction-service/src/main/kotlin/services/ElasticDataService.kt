package dk.sdu.cloud.ucloud.data.extraction.services

import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient

class ElasticDataService(val elasticHighLevelClient: RestHighLevelClient, val elasticLowLevelClient: RestClient) {
}
