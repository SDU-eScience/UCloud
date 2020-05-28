package app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.Roles
import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationWithFavoriteAndTags
import dk.sdu.cloud.app.store.services.EmbeddedNameAndVersion
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
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
    permission: ApplicationAccessRight
): Boolean {
    if (user.role in Roles.PRIVILEDGED) return true
    if (isPublic(session, user, appName, appVersion)) return true
    return aclDAO.hasPermission(
        session,
        user,
        project,
        memberGroups,
        appName,
        setOf(permission)
    )
}

internal suspend fun internalFindAllByName(
    ctx: DBContext,
    user: SecurityPrincipal?,
    currentProject: String?,
    projectGroups: List<String>,
    appName: String,
    paging: NormalizedPaginationRequest
): Page<ApplicationWithFavoriteAndTags> {
    val groups = if (projectGroups.isEmpty()) {
        listOf("")
    } else {
        projectGroups
    }

    return preparePageForUser(
        session,
        user?.username,
        session.createNativeQuery<ApplicationEntity>(
            """
                    select * from {h-schema}applications as A
                    where A.name = :name and (
                        (
                            A.is_public = TRUE
                        ) or (
                            cast(:project as text) is null and :user in (
                                select P1.username from {h-schema}permissions as P1 where P1.application_name = A.name
                            )
                        ) or (
                            cast(:project as text) is not null and exists (
                                select P2.project_group from {h-schema}permissions as P2 where
                                    P2.application_name = A.name and
                                    P2.project = cast(:project as text) and
                                    P2.project_group in (:groups)
                            )
                        ) or (
                            :role in (:privileged)
                        ) 
                    )
                    order by A.created_at desc
                """.trimIndent(), ApplicationEntity::class.java
        ).setParameter("user", user?.username ?: "")
            .setParameter("name", appName)
            .setParameter("project", currentProject)
            .setParameterList("groups", groups)
            .setParameter("role", user?.role ?: Role.UNKNOWN)
            .setParameterList("privileged", Roles.PRIVILEDGED)
            .resultList.paginate(paging).mapItems { it.toModelWithInvocation() }
    )
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

internal suspend fun retrieveUserProjectGroups(user: SecurityPrincipal, project: String): List<String> =
    ProjectMembers.userStatus.call(
        UserStatusRequest(user.username),
        authenticatedClient
    ).orRethrowAs {
        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }.groups.filter { it.projectId == project }.map { it.group }


internal suspend fun findOwnerOfApplication(ctx: DBContext, applicationName: String): String? {
    return session.criteria<ApplicationEntity> {
        entity[ApplicationEntity::id][EmbeddedNameAndVersion::name] equal applicationName
    }.apply {
        maxResults = 1
    }.uniqueResult()?.owner
}

internal fun canUserPerformWriteOperation(owner: String, user: SecurityPrincipal): Boolean {
    if (user.role == Role.ADMIN) return true
    return owner == user.username
}

internal fun normalizeQuery(query: String): String {
    return query.toLowerCase()
}
