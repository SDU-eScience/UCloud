package dk.sdu.cloud.app.store.services.acl

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.store.api.AccessEntity
import dk.sdu.cloud.app.store.api.ApplicationAccessRight
import dk.sdu.cloud.app.store.api.EntityWithPermission
import dk.sdu.cloud.service.db.async.DBContext

interface AclDao {
    suspend fun hasPermission(
        ctx: DBContext,
        user: SecurityPrincipal,
        project: String?,
        memberGroups: List<String>,
        appName: String,
        permission: Set<ApplicationAccessRight>
    ): Boolean

    suspend fun updatePermissions(
        ctx: DBContext,
        entity: AccessEntity,
        applicationName: String,
        permissions: ApplicationAccessRight
    )

    suspend fun revokePermission(
        ctx: DBContext,
        entity: AccessEntity,
        applicationName: String
    )

    suspend fun listAcl(
        ctx: DBContext,
        applicationName: String
    ): List<EntityWithPermission>
}
