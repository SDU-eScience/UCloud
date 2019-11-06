package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.app.license.services.acl.AclService

class AppLicenseService(
    private val aclService: AclService<*>
) {
    public fun hasPermission(entity: String, serverId: String, permission: AccessRight) : Boolean {
        this.listServers(entity, appName, appVersion).
    }
}