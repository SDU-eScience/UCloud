package dk.sdu.cloud.app.store.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.*
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.ApplicationWithFavoriteAndTags
import dk.sdu.cloud.app.store.api.NameAndVersion
import dk.sdu.cloud.app.store.api.ToolBackend
import dk.sdu.cloud.app.store.services.acl.AclAsyncDao
import dk.sdu.cloud.auth.api.AuthProviders
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.project.api.ProjectMembers
import dk.sdu.cloud.project.api.UserStatusRequest
import dk.sdu.cloud.service.NormalizedPaginationRequest
import dk.sdu.cloud.service.Page
import dk.sdu.cloud.service.db.async.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/*
* Avoid using if possible, especially in loops
*/
internal suspend fun internalHasPermission(
    ctx: DBContext,
    actorAndProject: ActorAndProject,
    memberGroups: List<String>,
    appName: String,
    appVersion: String?,
    permission: ApplicationAccessRight,
    publicService: ApplicationPublicService,
    aclDao: AclAsyncDao
): Boolean {
    if ((actorAndProject.actor as? Actor.User)?.principal?.role in Roles.PRIVILEGED) return true

    if (appVersion != null) {
        if (ctx.withSession { session ->
                publicService.isPublic(session, actorAndProject, appName, appVersion)
            }) return true
    }

    if (ctx.withSession { session ->
            publicService.anyIsPublic(session, actorAndProject, appName)
        }) return true

    return ctx.withSession { session ->
        aclDao.hasPermission(
            session,
            actorAndProject,
            memberGroups,
            appName,
            setOf(permission)
        )
    }
}

internal suspend fun internalByNameAndVersion(
    ctx: DBContext,
    appName: String,
    appVersion: String?
): RowData? {
    return ctx.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("name", appName)
                setParameter("version", appVersion)
            },
            """
                SELECT *
                FROM app_store.applications
                WHERE (name = :name) AND (version = :version or :version::text is null)
                ORDER BY created_at DESC 
            """
        ).rows.firstOrNull()
    }
}

internal suspend fun retrieveUserProjectGroups(
    actorAndProject: ActorAndProject,
    authenticatedClient: AuthenticatedClient
): List<String> =
    ProjectMembers.userStatus.call(
        UserStatusRequest(actorAndProject.actor.username),
        authenticatedClient
    ).orRethrowAs {
        throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
    }.groups.filter { it.project == actorAndProject.project }.map { it.group }


internal suspend fun findOwnerOfApplication(ctx: DBContext, applicationName: String): String? {
    return ctx.withSession { session ->
        session.sendPreparedStatement(
            {
                setParameter("appname", applicationName)
            },
            """
                SELECT *
                FROM app_store.applications
                WHERE name = :appname
                LIMIT 1
            """.trimIndent()
        ).rows.singleOrNull()?.getString("owner")!!
    }
}

internal fun normalizeQuery(query: String): String {
    return query.toLowerCase()
}

suspend fun verifyToolUpdatePermission(
    actorAndProject: ActorAndProject,
    session: AsyncDBConnection,
    toolName: String,
) {
    val username = actorAndProject.actor.safeUsername()
    val isProvider = username.startsWith(AuthProviders.PROVIDER_PREFIX)
    val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
    if (isProvider) {
        val rows = session
            .sendPreparedStatement(
                {
                    setParameter("name", toolName)
                },
                """
                    select tool.tool->>'backend', tool.tool->'supportedProviders'
                    from
                        app_store.tools tool
                    where
                        tool.name = :name
                """
            )
            .rows

        if (rows.isEmpty()) throw ApplicationException.NotFound()

        for (row in rows) {
            val backend = ToolBackend.valueOf(row.getString(0)!!)
            val supportedProviders = row.getString(1)
                // NOTE(Dan): Normalize JSONB null to normal null (both are possible here)
                .takeIf { it != "null" }
                ?.let { defaultMapper.decodeFromString(ListSerializer(String.serializer()), it) }
                ?: throw ApplicationException.NotFound()

            if (backend != ToolBackend.NATIVE) throw ApplicationException.NotFound()
            if (supportedProviders != listOf(providerName)) throw ApplicationException.NotFound()
        }
    }
}

suspend fun verifyAppUpdatePermission(
    actorAndProject: ActorAndProject,
    session: AsyncDBConnection,
    appName: String,
    appVersion: String? = null,
) {
    val username = actorAndProject.actor.safeUsername()
    val isProvider = username.startsWith(AuthProviders.PROVIDER_PREFIX)
    val providerName = username.removePrefix(AuthProviders.PROVIDER_PREFIX)
    if (isProvider) {
        val rows = session
            .sendPreparedStatement(
                {
                    setParameter("name", appName)
                    setParameter("version", appVersion)
                },
                """
                    select tool.tool->>'backend', tool.tool->'supportedProviders'
                    from
                        app_store.applications app join
                        app_store.tools tool on
                            app.tool_name = tool.name and app.tool_version = tool.version
                    where
                        app.name = :name and
                        (:version::text is null or app.version = :version)
                """
            )
            .rows

        if (rows.isEmpty()) throw ApplicationException.NotFound()

        for (row in rows) {
            val backend = ToolBackend.valueOf(row.getString(0)!!)
            val supportedProviders = row.getString(1)
                // NOTE(Dan): Normalize JSONB null to normal null (both are possible here)
                .takeIf { it != "null" }
                ?.let { defaultMapper.decodeFromString(ListSerializer(String.serializer()), it) }
                ?: throw ApplicationException.NotFound()

            if (backend != ToolBackend.NATIVE) throw ApplicationException.NotFound()
            if (supportedProviders != listOf(providerName)) throw ApplicationException.NotFound()
        }
    }
}
