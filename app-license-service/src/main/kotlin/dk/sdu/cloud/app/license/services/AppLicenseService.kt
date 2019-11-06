package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer
import dk.sdu.cloud.app.license.services.acl.AclService

class AppLicenseService(
    private val aclService: AclService<*>
) {
    public fun hasPermission(entity: String, appName: String, appVersion: String) : Boolean {
        this.listServers(entity, appName, appVersion).
    }

    public fun listServers(entity: String, appName: String, appVersion: String) : List<ApplicationLicenseServer> {

        for(licenseServer in appLicenseServers) {
            if(aclService.hasPermission(entity, licenseServer, AccessRight.READ)
        }
    }
}