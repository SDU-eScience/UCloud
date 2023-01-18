package dk.sdu.cloud.app.store.services

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable

/**
 * An application as it is represented in elasticsearch
 */

@Serializable
data class ElasticIndexedApplication(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("version")
    val version: String,
    @JsonProperty("description")
    val description: String,
    @JsonProperty("title")
    val title: String,
    @JsonProperty("tags")
    val tags: List<String>
) {
    @Suppress("unused")
    companion object {
        val NAME_FIELD = ElasticIndexedApplication::name
        val VERSION_FIELD = ElasticIndexedApplication::version
        val DESCRIPTION_FIELD = ElasticIndexedApplication::description
        val TITLE_FIELD = ElasticIndexedApplication::title
        val TAGS_FIELD = ElasticIndexedApplication::tags
    }
}
