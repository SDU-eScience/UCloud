package dk.sdu.cloud.app.license.api

data class ApplicationLicenseServer(
    val id: String,
    val name: String,
    val version: String,
    val address: String) {
}