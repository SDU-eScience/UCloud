package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AccessTokenContents
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.JSONB_TYPE
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.get
import org.hibernate.annotations.NaturalId
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.ManyToOne
import javax.persistence.Table

@Entity
@Table(name = "opaque_tokens")
data class OpaqueTokenEntity(
    @Id
    @NaturalId
    val token: String,

    @ManyToOne
    val user: PrincipalEntity,

    @Type(type = JSONB_TYPE)
    val scopes: List<String>,

    val createdAt: Date,

    val expiresAt: Date?,

    val claimableId: String?,

    val sessionReference: String?,

    val extendedBy: String?
) {
    companion object : HibernateEntity<OpaqueTokenEntity>, WithId<String>
}

class OpaqueAccessTokenHibernateDao : OpaqueAccessTokenDao<HibernateSession> {
    override fun find(session: HibernateSession, token: String): AccessTokenContents? {
        return OpaqueTokenEntity[session, token]?.let { entity ->
            AccessTokenContents(
                entity.user.toModel(),
                entity.scopes.map { SecurityScope.parseFromString(it) },
                entity.createdAt.time,
                entity.expiresAt?.time,
                entity.claimableId,
                entity.sessionReference,
                entity.extendedBy
            )
        }
    }

    override fun insert(session: HibernateSession, token: String, contents: AccessTokenContents) {
        val entity = OpaqueTokenEntity(
            token,
            contents.user.toEntity(),
            contents.scopes.map { it },
            Date(contents.createdAt),
            contents.expiresAt?.let { Date(it) },
            contents.claimableId,
            contents.sessionReference,
            contents.extendedBy
        )

        session.save(entity)
    }

    override fun revoke(session: HibernateSession, token: String) {
        OpaqueTokenEntity[session, token]?.let { session.delete(it) }
    }
}
