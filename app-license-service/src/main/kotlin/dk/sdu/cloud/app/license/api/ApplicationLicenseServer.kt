package dk.sdu.cloud.app.license.api
import dk.sdu.cloud.AccessRight

data class ApplicationLicenseServer(
    val server: String,
    val version: String,
    val address: String) {

    public fun hasPermission(entity: Entity, right: AccessRight) : Boolean {
        return false
    }

    public fun updatePermission(entity: Entity, right: AccessRight) {}
}