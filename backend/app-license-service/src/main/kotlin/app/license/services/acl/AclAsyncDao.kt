package dk.sdu.cloud.app.license.services.acl

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.app.license.api.*
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession

object PermissionTable : SQLTable ("permissions") {
    val user = text("username", notNull = true)
    val project = text("project", notNull = true)
    val group = text("project_group", notNull = true)
    val serverId = text("server_id", notNull = true)
    val permission = text("permission", notNull = true)
}

class AclAsyncDao {
    suspend fun hasPermission(
        db: DBContext,
        serverId: String,
        accessEntity: AccessEntity,
        permission: ServerAccessRight
    ): Boolean {
        return db.withSession { session ->
            var query = """
                SELECT * 
                FROM permissions
                WHERE 
            """.trimIndent()
            query += if (accessEntity.user != null) {
                """ (username = ?user) """
            } else {
                """ (project = ?project AND project_group = ?group) """
            }
            query += if (permission == ServerAccessRight.READ_WRITE) {
                """ AND (permission = ?write)"""
            } else {
                """ AND (permission = ?read OR permission = ?write)"""
            }
            session
                .sendPreparedStatement(
                    {
                        if (accessEntity.user != null) {
                            setParameter("user", accessEntity.user ?: "")
                        } else {
                            setParameter("project", accessEntity.project ?: "")
                            setParameter("group", accessEntity.group ?: "")
                        }
                        if (permission == ServerAccessRight.READ_WRITE) {
                            setParameter("write", ServerAccessRight.READ_WRITE.toString())
                        } else {
                            setParameter("write", ServerAccessRight.READ_WRITE.toString())
                            setParameter("read", ServerAccessRight.READ.toString())
                        }
                    },
                    query
                ).rows.isNotEmpty()
        }
    }

    suspend fun updatePermissions(
        db: DBContext,
        serverId: String,
        accessEntity: AccessEntity,
        permissions: ServerAccessRight
    ) {
        db.withSession { session ->
            val found = session
                .sendPreparedStatement(
                    {
                        setParameter("server", serverId)
                        setParameter("user", accessEntity.user ?: "")
                        setParameter("project", accessEntity.project ?: "")
                        setParameter("group", accessEntity.group ?: "")
                    },
                    """
                        SELECT *
                        FROM permissions
                        WHERE (server_id = :server) AND
                            (project = :project) AND 
                            (username = :user) AND
                            (project_group = :group)                        
                    """
                ).rows.singleOrNull()

            if (found != null) {
                session
                    .sendPreparedStatement(
                    {
                        setParameter("permission", permissions.toString())
                        setParameter("server", serverId)
                        setParameter("user", accessEntity.user ?: "")
                        setParameter("project", accessEntity.project ?: "")
                        setParameter("group", accessEntity.group ?: "")
                    },
                    """
                        UPDATE permissions
                        SET permission = :permission
                        WHERE (server_id = :server) AND
                            (project = :project) AND 
                            (username = :user) AND
                            (project_group = :group)
                    """
                )
            } else {
                session.insert(PermissionTable) {
                    set(PermissionTable.permission, permissions.toString())
                    set(PermissionTable.group, accessEntity.group ?: "")
                    set(PermissionTable.project, accessEntity.project ?: "")
                    set(PermissionTable.user, accessEntity.user ?: "")
                    set(PermissionTable.serverId, serverId)
                }
            }
        }
    }

    suspend fun revokePermission(
        db: DBContext,
        serverId: String,
        accessEntity: AccessEntity
    ) {
        db.withSession { session ->
            if (accessEntity.user.isNullOrBlank()) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("serverID", serverId)
                            setParameter("project", accessEntity.project)
                            setParameter("group", accessEntity.group)
                        },
                        """
                            DELETE FROM permissions
                            WHERE (server_id = :serverID) AND
                                (project = :project) AND
                                (project_group = :group)
                        """
                    )
            } else {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("serverID", serverId)
                            setParameter("user", accessEntity.user)
                        },
                        """
                            DELETE FROM permissions
                            WHERE (server_id = :serverID) AND (username = :user)
                        """
                    )
            }
        }
    }

    suspend fun revokeAllServerPermissions(
        db: DBContext,
        serverId: String
    ) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverID", serverId)
                    },
                    """
                        DELETE FROM permissions
                        WHERE server_id = :serverID
                    """
                )
        }
    }

    suspend fun listAcl(
        db: DBContext,
        serverId: String
    ): List<AccessEntityWithPermission> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("serverID", serverId)
                    },
                    """
                        SELECT *
                        FROM permissions
                        WHERE server_id = :serverID
                    """
                ).rows
        }.map { it.toAccessWithPermission() }
    }

    suspend fun revokePermissionsFromEntity(db: DBContext, accessEntity: AccessEntity) {
        db.withSession { session ->
            if (accessEntity.user.isNullOrBlank()) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("project", accessEntity.project)
                            setParameter("group", accessEntity.group)
                        },
                        """
                            DELETE FROM permissions
                            WHERE (project = :project) AND (project_group = :group)
                        """
                    )
            } else {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("user", accessEntity.user)
                        },
                        """
                            DELETE FROM permissions
                            WHERE username = :user
                        """
                    )
            }
        }
    }

    fun RowData.toAccessWithPermission(): AccessEntityWithPermission {
        return AccessEntityWithPermission(
            AccessEntity(
                getField(PermissionTable.user),
                getField(PermissionTable.project),
                getField(PermissionTable.group)
            ),
            ServerAccessRight.valueOf(getField(PermissionTable.permission))
        )
    }
}
