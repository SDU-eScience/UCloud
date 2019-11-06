package dk.sdu.cloud.app.license.services.acl

import dk.sdu.cloud.AccessRight
import dk.sdu.cloud.app.license.api.ApplicationLicenseServer

data class UserWithPermissions(val username: String, val permissions: Set<AccessRight>)

interface AclDao<Session> {
    //fun updatePermissions(session: Session, username: String, permissions: Set<AccessRight>)
    fun hasPermission(session: Session, licenseServer: ApplicationLicenseServer<Session>, entity: String, permission: AccessRight): Boolean
    //fun listAcl(session: Session, paths: List<String>): Map<String, List<UserWithPermissions>>
    //fun revokePermission(session: Session, path: String, username: String)
    //fun handleFilesMoved(session: Session, oldPath: String, newPath: String)
    //fun handleFilesDeleted(session: Session, paths: List<String>)
    //fun dumpAllForDebugging(session: Session): Map<String, List<UserWithPermissions>>
}
