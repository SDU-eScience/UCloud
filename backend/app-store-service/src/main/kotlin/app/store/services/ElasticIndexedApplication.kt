package dk.sdu.cloud.app.store.services

/**
 * An application as it is represented in elasticsearch
 */

data class ElasticIndexedApplication(
    val name: String,
    val version: String,
    val description: String,
    val title: String,
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
