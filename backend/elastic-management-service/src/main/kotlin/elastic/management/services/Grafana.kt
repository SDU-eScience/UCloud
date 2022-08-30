package elastic.management.services

import dk.sdu.cloud.service.Loggable
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.GetIndexRequest
import org.slf4j.Logger
import java.time.LocalDate

const val DAYS_IN_PAST = 7L

class Grafana(val elastic: RestHighLevelClient) {

    fun createAlias(alias: String, prefix: String) {
        val indicesSearchList = mutableListOf<String>()
        for (i in 0..DAYS_IN_PAST) {
            val date = LocalDate.now().minusDays(i).toString().replace("-","." )
            indicesSearchList.add("$prefix*$date*")
        }
        val newIndices = elastic
            .indices()
            .get(
                GetIndexRequest(indicesSearchList.joinToString(",")),
                RequestOptions.DEFAULT
            ).indices.toList()

        val indicesAddAliasesRequest = IndicesAliasesRequest()
        val aliasAddAction = IndicesAliasesRequest
            .AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
            .indices(*newIndices.toTypedArray())
            .alias(alias)

        indicesAddAliasesRequest.addAliasAction(aliasAddAction)

        log.info("Adding Alias: $alias to $newIndices")
        elastic.indices().updateAliases(indicesAddAliasesRequest, RequestOptions.DEFAULT)

        val grafanaIndices = elastic
            .indices()
            .get(
                GetIndexRequest(alias),
                RequestOptions.DEFAULT
            ).indices.toList()

        val oldIndices = grafanaIndices.filter { index ->
            index.contains(LocalDate.now().minusDays(DAYS_IN_PAST+1).toString().replace("-","." ))
        }

        if (oldIndices.isNotEmpty()) {
            val indicesRemoveAliasesRequest = IndicesAliasesRequest()

            val aliasRemoveAction = IndicesAliasesRequest
                .AliasActions(IndicesAliasesRequest.AliasActions.Type.REMOVE)
                .indices(*oldIndices.toTypedArray())
                .alias(alias)

            indicesRemoveAliasesRequest.addAliasAction(aliasRemoveAction)

            log.info("Removing Alias: $alias from $oldIndices")
            elastic.indices().updateAliases(indicesRemoveAliasesRequest, RequestOptions.DEFAULT)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }

}
