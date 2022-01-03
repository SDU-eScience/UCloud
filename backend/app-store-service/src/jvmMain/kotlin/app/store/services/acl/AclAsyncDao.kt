package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.AccessEntity
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession

object PermissionTable : SQLTable("permissions") {
    val user = text("username", notNull = true)
    val project = text("project", notNull = true)
    val group = text("project_group", notNull = true)
    val applicationName = text("application_name", notNull = true)
    val permission = text("permission", notNull = true)
}

class AclAsyncDao {
     suspend fun hasPermission(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        applicationName: String,
        permissions: Set<ApplicationAccessRight>
    ): Boolean {
        val result = ctx.withSession{ session ->
            session.sendPreparedStatement(
                {
                    setParameter("user", user.username)
                    setParameter("project", project)
                    setParameter("groups", memberGroups)
                    setParameter("appname", applicationName)
                },
                """
                    SELECT *
                    FROM permissions
                    WHERE (username = ?user) OR
                        (
                            (project = ?project) AND
                            (project_group IN (select unnest (?groups::text[])))
                        ) AND
                        (application_name = ?appname)
                """.trimIndent()
            ).rows.singleOrNull()
        }

        if (!result.isNullOrEmpty()) {
            return permissions.contains(ApplicationAccessRight.valueOf(result.getField(PermissionTable.permission)))
        }
        return false
    }

    suspend fun updatePermissions(
        ctx: DBContext,
        accessEntity: AccessEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    ) {
        ctx.withSession { session ->
            val permission = session.sendPreparedStatement(
                {
                    setParameter("appname", applicationName)
                    setParameter("user", accessEntity.user)
                    setParameter("project", accessEntity.project)
                    setParameter("group", accessEntity.group)
                },
                """
                    SELECT * 
                    FROM permissions
                    WHERE (application_name = ?appname) AND
                     (username = ?user) AND (project = ?project)
                     AND (project_group = ?group)
                """.trimIndent()
            ).rows.singleOrNull()

            if (permission != null) {
                session.sendPreparedStatement(
                    {
                        setParameter("permission", permissions.toString())
                        setParameter("appname", applicationName)
                        setParameter("user", accessEntity.user)
                        setParameter("project", accessEntity.project)
                        setParameter("group", accessEntity.group)
                    },
                    """
                        UPDATE permissions
                        SET permission = ?permission
                        WHERE (application_name = ?appname) AND
                            (username = ?user) AND (project = ?project)
                            AND (project_group = ?group)                    
                    """.trimIndent()
                )
            } else {
                session.insert(PermissionTable) {
                    set(PermissionTable.permission, permissions.toString())
                    set(PermissionTable.applicationName, applicationName)
                    set(PermissionTable.group, accessEntity.group ?: "")
                    set(PermissionTable.project, accessEntity.project ?: "")
                    set(PermissionTable.user, accessEntity.user ?: "")
                }
            }
        }
    }

    suspend fun revokePermission(
        ctx: DBContext,
        accessEntity: AccessEntity,
        applicationName: String
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", applicationName)
                    setParameter("user", accessEntity.user ?: "")
                    setParameter("project", accessEntity.project ?: "")
                    setParameter("group", accessEntity.group ?: "")
                },
                """
                    DELETE FROM permissions
                    WHERE (application_name = ?appname) AND
                            (username = ?user) AND (project = ?project)
                            AND (project_group = ?group)     
                """.trimIndent()
            )
        }
    }

    suspend fun listAcl(
        ctx: DBContext,
        applicationName: String
    ): List<EntityWithPermission> {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("appname", applicationName)
                },
                """
                    SELECT *
                    FROM permissions
                    WHERE application_name = ?appname
                """.trimIndent()
            ).rows.map {
                EntityWithPermission(
                    AccessEntity(
                        it.getField(PermissionTable.user),
                        it.getField(PermissionTable.project),
                        it.getField(PermissionTable.group)
                    ),
                    ApplicationAccessRight.valueOf(it.getField(PermissionTable.permission))
                )
            }
        }
    }
}
