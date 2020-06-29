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
        db.withTransaction { session ->
            tagDao.createTags(session, user, applicationName, tags)
        }
        elasticDao.addTagToElastic(applicationName, tags)

    }

    suspend fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            tagDao.deleteTags(session, user, applicationName, tags)
        }
        elasticDao.removeTagFromElastic(applicationName, tags)
    }

}
