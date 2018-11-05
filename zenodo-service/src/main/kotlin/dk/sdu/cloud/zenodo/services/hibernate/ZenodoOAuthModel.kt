package dk.sdu.cloud.zenodo.services.hibernate

import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.WithTimestamps
import dk.sdu.cloud.zenodo.services.OAuthTokens
import org.hibernate.annotations.NaturalId
import java.util.Date
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

@Entity
@Table(name = "zen_oauth_state_tokens")
internal data class ZenStateTokenEntity(
    @Id
    @NaturalId
    var owner: String,

    var token: String = "",

    var returnTo: String = "",

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<ZenStateTokenEntity>, WithId<String>
}

@Entity
@Table(name = "zen_oauth_tokens")
internal data class ZenOAuthTokenEntity(
    @Id
    @NaturalId
    var owner: String,

    var accessToken: String = "",

    var refreshToken: String = "",

    var expiresAt: Date = Date(0),

    override var createdAt: Date = Date(),

    override var modifiedAt: Date = Date()
) : WithTimestamps {
    companion object : HibernateEntity<ZenOAuthTokenEntity>, WithId<String>
}

internal fun ZenOAuthTokenEntity.toModel(): OAuthTokens = OAuthTokens(accessToken, expiresAt.time, refreshToken)
