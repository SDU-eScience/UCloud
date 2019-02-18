package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.auth.api.AuthDescriptions
import dk.sdu.cloud.auth.api.BulkInvalidateRequest
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class TokenInvalidator<DBSession>(
    private val serviceCloud: AuthenticatedClient,
    private val db: DBSessionFactory<DBSession>,
    private val tokenDao: AuthTokenDao<DBSession>
) {
    suspend fun invalidateTokensForProject(project: String) {
        log.info("Invalidating tokens for project=$project!")
        val tokens = db.withTransaction { tokenDao.tokensForProject(it, project) }
        log.debug("Found ${tokens.size} for project")
        AuthDescriptions.bulkInvalidate
            .call(
                BulkInvalidateRequest(tokens.map { it.authRefreshToken }),
                serviceCloud
            )
            .orThrow()

        log.debug("Tokens successfully invalidated at central location")
        db.withTransaction { tokenDao.invalidateTokensForProject(it, project) }
    }

    companion object : Loggable {
        override val log = logger()
    }
}
