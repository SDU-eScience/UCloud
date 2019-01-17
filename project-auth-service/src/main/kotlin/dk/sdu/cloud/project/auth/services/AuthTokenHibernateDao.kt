package dk.sdu.cloud.project.auth.services

import dk.sdu.cloud.project.api.ProjectRole
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.deleteCriteria
import dk.sdu.cloud.service.db.get
import java.io.Serializable
import javax.persistence.Embeddable
import javax.persistence.EmbeddedId
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Table

@Entity
@Table(name = "auth_tokens")
data class AuthTokenEntity(
    @EmbeddedId
    var id: AuthTokenId,

    var refreshToken: String
) {
    companion object : HibernateEntity<AuthTokenEntity>, WithId<AuthTokenId>
}

@Embeddable
data class AuthTokenId(
    val project: String,
    @Enumerated(EnumType.STRING)
    val role: ProjectRole
) : Serializable

class AuthTokenHibernateDao : AuthTokenDao<HibernateSession> {
    override fun storeToken(session: HibernateSession, token: AuthToken) {
        log.debug("storing token for ${token.project}, ${token.role}")

        val entity = AuthTokenEntity(
            AuthTokenId(token.project, token.role),
            token.authRefreshToken
        )

        session.save(entity)
    }

    override fun retrieveTokenForProjectInRole(
        session: HibernateSession,
        project: String,
        role: ProjectRole
    ): AuthToken {
        log.debug("Searching after a token for $project, $role...")
        val entity = AuthTokenEntity[session, AuthTokenId(project, role)] ?: throw AuthTokenException.NotFound()
        log.debug("... Token found!")
        return AuthToken(entity.refreshToken, project, role)
    }

    override fun invalidateTokensForProject(session: HibernateSession, project: String) {
        log.info("Invalidating tokens for $project")
        val updateCount = session.deleteCriteria<AuthTokenEntity> {
            entity[AuthTokenEntity::id][AuthTokenId::project] equal project
        }.executeUpdate()

        if (updateCount <= 0) throw AuthTokenException.NotFound()
        log.debug("... Affected $updateCount rows")
    }

    override fun tokensForProject(session: HibernateSession, project: String): List<AuthToken> {
        log.debug("Searching for all tokens in a project")
        return session
            .criteria<AuthTokenEntity> {
                entity[AuthTokenEntity::id][AuthTokenId::project] equal project
            }
            .list()
            .map { AuthToken(it.refreshToken, it.id.project, it.id.role) }
            .takeIf { it.isNotEmpty() } ?: throw AuthTokenException.NotFound()
    }

    companion object : Loggable {
        override val log = logger()
    }
}

