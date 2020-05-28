package app.store.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.services.ElasticDAO
import dk.sdu.cloud.service.db.async.AsyncDBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ApplicationTagsService (
    private val db: AsyncDBSessionFactory,
    private val tagDAO: ApplicationTagsAsyncDAO,
    private val elasticDAO: ElasticDAO
) {
    suspend fun createTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            tagDAO.createTags(session, user, applicationName, tags)
        }
        elasticDAO.addTagToElastic(applicationName, tags)

    }

    suspend fun deleteTags(tags: List<String>, applicationName: String, user: SecurityPrincipal) {
        db.withTransaction { session ->
            tagDAO.deleteTags(session, user, applicationName, tags)
        }
        elasticDAO.removeTagFromElastic(applicationName, tags)
    }

}
