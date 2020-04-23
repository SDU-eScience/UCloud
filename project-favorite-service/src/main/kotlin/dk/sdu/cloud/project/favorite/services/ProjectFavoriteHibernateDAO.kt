package dk.sdu.cloud.project.favorite.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.get

class ProjectFavoriteHibernateDAO: ProjectFavoriteDAO<HibernateSession> {
    override fun listFavorites(
        session: HibernateSession,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<String> {
        val itemsInTotal = session.createCriteriaBuilder<Long, FavoriteProjectEntity>().run {
            criteria.where(entity[FavoriteProjectEntity::user] equal user.username)
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult().toInt()

        if (itemsInTotal == 0) {
            return Page(
                itemsInTotal,
                0,
                0,
                emptyList()
            )
        }
        else {
            val favorites = session.createCriteriaBuilder<String, FavoriteProjectEntity>().run {
                criteria.where(entity[FavoriteProjectEntity::user] equal user.username)
                criteria.select(entity[FavoriteProjectEntity::projectID])
            }.createQuery(session).resultList.toList()

            return Page(
                itemsInTotal,
                paging.itemsPerPage,
                paging.page,
                favorites
            )
        }
    }

    override fun toggleFavorite(session: HibernateSession, user: SecurityPrincipal, projectID: String) {
        if (isFavorite(session, user.username, projectID)) {
            val query = session.createQuery(
                """
                    delete from FavoriteProjectEntity as F
                    where F.user = :user
                        and F.projectID = :projectID
                """.trimIndent()
            ).setParameter("user", user.username)
                .setParameter("projectID", projectID)

            query.executeUpdate()
        } else {
            session.save(
                FavoriteProjectEntity(
                    projectID,
                    user.username
                )
            )
        }
    }

    private fun isFavorite(session: HibernateSession, username: String, projectID: String): Boolean {
        return 0L != session.createCriteriaBuilder<Long, FavoriteProjectEntity>().run {
            criteria.where(
                (entity[FavoriteProjectEntity::user] equal username)
                        and (entity[FavoriteProjectEntity::projectID] equal projectID)
            )
            criteria.select(count(entity))
        }.createQuery(session).uniqueResult()
    }
}
