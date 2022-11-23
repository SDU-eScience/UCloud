package dk.sdu.cloud.app.store.services

import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ApplicationTagsService (
    private val db: AsyncDBSessionFactory,
    private val tagDao: ApplicationTagsAsyncDao,
    private val elasticDao: ElasticDao?
) {
    suspend fun createTags(tags: List<String>, applicationName: String) {
        db.withTransaction { session ->
            tagDao.createTags(session, applicationName, tags)
        }
        elasticDao?.addTagToElastic(applicationName, tags)

    }

    suspend fun deleteTags(tags: List<String>, applicationName: String) {
        db.withTransaction { session ->
            tagDao.deleteTags(session, applicationName, tags)
        }
        elasticDao?.removeTagFromElastic(applicationName, tags)
    }

    suspend fun listTags(): List<String> {
        return db.withTransaction { session ->
            tagDao.listTags(session)
        }
    }

}
