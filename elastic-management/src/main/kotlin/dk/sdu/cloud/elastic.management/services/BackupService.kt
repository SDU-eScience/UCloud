package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.client.RestHighLevelClient

class BackupService(private val elastic: RestHighLevelClient) {

    fun start(): String {
        return "hello"
    }
}
