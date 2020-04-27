package dk.sdu.cloud.project.favorite.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.async.AsyncDBConnection
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.createCriteriaBuilder
import dk.sdu.cloud.service.db.createQuery
import dk.sdu.cloud.service.db.get
import io.ktor.http.HttpStatusCode
import java.lang.reflect.Array.set

class ProjectFavoriteDAO {
    suspend fun listFavorites(
        session: AsyncDBConnection,
        user: SecurityPrincipal,
        paging: NormalizedPaginationRequest
    ): Page<String> {
        val itemsInTotal = session.sendPreparedStatement(
            {
                setParameter("username", user.username)
            },
            """
                select count(*)
                from project_favorite
                where 
                    username = ?username 
            """.trimIndent()
        ).rows
            .map { it.getString(0)?.toInt()}.singleOrNull() ?: throw RPCException.fromStatusCode(HttpStatusCode.BadRequest)

        if (itemsInTotal == 0) {
            return Page(
                itemsInTotal,
                0,
                0,
                emptyList()
            )
        }
        else {
            val favorites = session.sendPreparedStatement(
                {
                    setParameter("username", user.username)
                },
                """
                select *
                from project_favorite
                where 
                    username = ?username 
            """.trimIndent()
            ).rows.map{it.toProjectFavoriteID()}

            return Page(
                itemsInTotal,
                paging.itemsPerPage,
                paging.page,
                favorites
            )
        }
    }

    suspend fun toggleFavorite(session: AsyncDBConnection, user: SecurityPrincipal, projectID: String) {
        if (isFavorite(session, user.username, projectID)) {
            session.sendPreparedStatement(
                {
                    setParameter("username", user.username)
                    setParameter("project_id", projectID)
                },
                """
                    delete from project_favorite
                    where 
                        username = ?username and 
                        project_id = ?project_id
                """
            )
        } else {
            session.insert(FavoriteProject) {
                set(FavoriteProject.project, projectID)
                set(FavoriteProject.username, user.username)
            }
        }
    }

    private suspend fun isFavorite(session: AsyncDBConnection, username: String, projectID: String): Boolean {
        return 0 != session.sendPreparedStatement(
            {
                setParameter("username", username)
                setParameter("project_id", projectID)
            },
            """
                    select count(*)
                    from project_favorite
                    where 
                        username = ?username and
                        project_id = ?project
                """
        ).rows
            .map { it.getString(0)?.toInt() }.singleOrNull()
    }

    private object FavoriteProject : SQLTable("project_favorite") {
        val username = text("username")
        val project = text("project_id")
    }

    private fun RowData.toProjectFavoriteID(): String = getField(FavoriteProject.project)

}
