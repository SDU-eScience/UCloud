package dk.sdu.cloud.app.services

import dk.sdu.cloud.app.api.NewApplication
import dk.sdu.cloud.app.api.NewNormalizedApplicationDescription
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import java.io.Serializable
import java.util.*
import javax.persistence.*

@Entity
@Table(
    name = "entities",
    uniqueConstraints = [
    ]
)
class ApplicationEntity(
    @EmbeddedId
    var id: EmbeddedNameAndVersion,

    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date,

    @Temporal(TemporalType.TIMESTAMP)
    var modifiedAt: Date
) {
    companion object : HibernateEntity<ApplicationEntity>, WithId<EmbeddedNameAndVersion>
}

data class EmbeddedNameAndVersion(
    var name: String = "",
    var version: String = ""
) : Serializable

class ApplicationHibernateDAO : ApplicationDAO2<HibernateSession> {
    override fun findAllByName(
        session: HibernateSession,
        user: String,
        name: String,
        paging: NormalizedPaginationRequest
    ): Page<NewApplication> {
        TODO("not implemented")
    }

    override fun findByNameAndVersion(
        session: HibernateSession,
        user: String,
        name: String,
        version: String
    ): NewApplication {
        TODO("not implemented")
    }

    override fun listLatestVersion(
        session: HibernateSession,
        user: String,
        paging: NormalizedPaginationRequest
    ): Page<NewApplication> {
        TODO("not implemented")
    }

    override fun create(session: HibernateSession, user: String, description: NewNormalizedApplicationDescription) {
        TODO("not implemented")
    }

    override fun updateDescription(
        session: HibernateSession,
        user: String,
        newDescription: String?,
        newAuthors: List<String>?
    ) {
        TODO("not implemented")
    }

}

