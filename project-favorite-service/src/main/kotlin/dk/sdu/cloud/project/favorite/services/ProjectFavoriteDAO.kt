package dk.sdu.cloud.project.favorite.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.WithId
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class FavoriteProjectEntity(
    var projectID: String,

    @Column(name = "the_user")
    var user: String,

    @Id
    @GeneratedValue
    var id: Long? = null
) {
    companion object : HibernateEntity<FavoriteProjectEntity>, WithId<Long>
}

interface ProjectFavoriteDAO<Session> {
    fun toggleFavorite(
        session: Session,
        user: SecurityPrincipal,
        projectID: String
    )

    fun listFavorites(
        session: Session,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<String>
}
