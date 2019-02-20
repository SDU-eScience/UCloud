package dk.sdu.cloud.elastic.management.services

import org.elasticsearch.client.RestHighLevelClient

class DeleteService(
    private val elastic: RestHighLevelClient
){

    fun findExpired() {

    }

    fun deleteFullIndex(){

    }

    fun deleteDocuments(){

    }

    fun cleanUp() {

    }
}
