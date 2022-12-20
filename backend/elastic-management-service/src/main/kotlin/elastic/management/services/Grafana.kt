package elastic.management.services

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.indices.GetIndexRequest
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest
import co.elastic.clients.elasticsearch.indices.update_aliases.AddAction
import co.elastic.clients.elasticsearch.indices.update_aliases.RemoveAction
import dk.sdu.cloud.service.Loggable
import org.elasticsearch.client.RequestOptions
import org.slf4j.Logger
import java.time.LocalDate

const val DAYS_IN_PAST = 7L

class Grafana(val elastic: ElasticsearchClient) {

    fun createAlias(alias: String, prefix: String) {
        val indicesSearchList = mutableListOf<String>()
        for (i in 0..DAYS_IN_PAST) {
            val date = LocalDate.now().minusDays(i).toString().replace("-","." )
            indicesSearchList.add("$prefix*$date*")
        }
        val newIndices = elastic
            .indices()
            .get(
                GetIndexRequest.Builder()
                    .index(indicesSearchList)
                    .build()
            ).result().keys.toList()

        val indicesAddAliasesRequest = UpdateAliasesRequest.Builder()
            .actions { action ->
                action.add(
                    AddAction.Builder()
                        .alias(alias)
                        .indices(newIndices)
                        .build()
                )
            }
            .build()

        log.info("Adding Alias: $alias to $newIndices")
        elastic.indices().updateAliases(
            indicesAddAliasesRequest
        )

        val grafanaIndices = elastic
            .indices()
            .get(
                GetIndexRequest.Builder()
                    .index(alias)
                    .build()
            ).result().keys.toList()

        val oldIndices = grafanaIndices.filter { index ->
            index.contains(LocalDate.now().minusDays(DAYS_IN_PAST+1).toString().replace("-","." ))
        }

        if (oldIndices.isNotEmpty()) {
            val indicesRemoveAliasesRequest = UpdateAliasesRequest.Builder()
                .actions { action ->
                    action.remove(
                        RemoveAction.Builder()
                            .indices(oldIndices)
                            .alias(alias)
                            .build()
                    )
                }
                .build()

            log.info("Removing Alias: $alias from $oldIndices")
            elastic.indices().updateAliases(indicesRemoveAliasesRequest)
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()
    }

}
