package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationWithFavoriteAndTags
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.mapItems
import dk.sdu.cloud.service.paginate
import io.ktor.http.HttpStatusCode

/*
* Avoid using if possible, especially in loops
*/
internal suspend fun internalHasPermission(
    ctx: DBContext,
    user: SecurityPrincipal,
    project: String?,
    memberGroups: List<String>,
    appName: String,
    appVersion: String,
    permission: ApplicationAccessRight,
    publicDao: ApplicationPublicAsyncDao,
    aclDao: AclAsyncDao
): Boolean {
    if (user.role in Roles.PRIVILEGED) return true
    if (ctx.withSession { session -> publicDao.isPublic(session, user, appName, appVersion)}) return true
    return ctx.withSession { session ->
        aclDao.hasPermission(
            session,
            user,
            project,
            memberGroups,
            appName,
            setOf(permission)
        )
    }
}

internal suspend fun internalFindAllByName(
    ctx: DBContext,
    user: SecurityPrincipal?,
    currentProject: String?,
    projectGroups: List<String>,
    appName: String,
    paging: NormalizedPaginationRequest,
    appStoreAsyncDao: AppStoreAsyncDao
): Page<ApplicationWithFavoriteAndTags> {
    val groups = if (projectGroups.isEmpty()) {
        listOf("")
    } else {
        projectGroups
    }

    return ctx.withSession { session ->
        appStoreAsyncDao.preparePageForUser(
            session,
            user?.username,
            session.sendPreparedStatement(
                {
                    setParameter("name", appName)
                    setParameter("project", currentProject)
                    setParameter("groups", groups)
                    setParameter("role", (user?.role ?: Role.UNKNOWN).toString())
                    setParameter("privileged", Roles.PRIVILEGED.toList())
                    setParameter("user", user?.username ?: "")
                },
                """
                    SELECT * FROM applications AS A
                    WHERE A.name = ?name AND (
                        (
                            A.is_public = TRUE
                        ) OR (
                            cast(?project as text) is null AND ?user IN (
                                SELECT P1.username FROM permissions AS P1 WHERE P1.application_name = A.name
                            )
                        ) OR (
                            cast(?project as text) is not null and exists (
                                SELECT P2.project_group FROM permissions AS P2 WHERE
                                    P2.application_name = A.name AND
                                    P2.project = cast(?project as text) AND
                                    P2.project_group in (?groups)
                            )
                        ) OR (
                            ?role in (?privileged)
                        ) 
                    )
                    ORDER BY A.created_at DESC
                """.trimIndent()
            ).rows.paginate(paging).mapItems { it.toApplicationWithInvocation() }
        )
    }
}

internal suspend fun internalByNameAndVersion(
    ctx: DBContext,
    appName: String,
    appVersion: String
): RowData? {
    return ctx.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("name", appName)
                setParameter("version", appVersion)
            },
            """
                    SELECT *
                    FROM applications
                    WHERE (name = ?name) AND (version = ?version)
                """.trimIndent()
        ).rows.singleOrNull()
    }
}

internal suspend fun retrieveUserProjectGroups(
    user: SecurityPrincipal,
    project: String,
    authenticatedClient: AuthenticatedClient
): List<String> =
    ProjectMembers.userStatus.call(
        UserStatusRequest(user.username),
        authenticatedClient
    ).orRethrowAs {
        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }.groups.filter { it.project == project }.map { it.group }


internal suspend fun findOwnerOfApplication(ctx: DBContext, applicationName: String): String? {
    return ctx.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("appname", applicationName)
            },
            """
                SELECT *
                FROM applications
                WHERE name = ?appname
                LIMIT 1
            """.trimIndent()
        ).rows.singleOrNull()?.getField(ApplicationTable.owner)
    }
}

internal fun canUserPerformWriteOperation(owner: String, user: SecurityPrincipal): Boolean {
    if (user.role == Role.ADMIN) return true
    return owner == user.username
}

internal fun normalizeQuery(query: String): String {
    return query.toLowerCase()
}
