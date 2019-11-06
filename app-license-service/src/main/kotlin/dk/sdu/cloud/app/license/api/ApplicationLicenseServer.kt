package dk.sdu.cloud.app.license.api
import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.services.acl.AclService
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction

class ApplicationLicenseServer<Session>(
    private val aclService: AclService<Session>,
    val id: String,
    val name: String,
    val version: String,
    val address: String) {

    public suspend fun hasPermission(entity: String, permission: AccessRight) : Boolean {
        return aclService.hasPermission(this.id, entity, permission)
    }

    public fun updatePermission(entity: String, right: AccessRight) {}
}