package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ApplicationTagsService (
    private val db: AsyncDBSessionFactory,
    private val tagDao: ApplicationTagsAsyncDao,
    private val elasticDao: ElasticDao
) {
    suspend fun createTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        val normalizedTags = tags.map { it.lowercase() }

        db.withTransaction { session ->
            tagDao.createTags(session, user, applicationName, normalizedTags)
        }
        elasticDao.addTagToElastic(applicationName, normalizedTags)
    }

    suspend fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        val normalizedTags = tags.map { it.lowercase() }

        db.withTransaction { session ->
            tagDao.deleteTags(session, user, applicationName, normalizedTags)
        }
        elasticDao.removeTagFromElastic(applicationName, normalizedTags)
    }

}
