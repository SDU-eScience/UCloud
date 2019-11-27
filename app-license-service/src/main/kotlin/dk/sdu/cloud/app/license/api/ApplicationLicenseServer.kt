package dk.sdu.cloud.app.license.api

data class ApplicationLicenseServer(
    val name: String,
    val address: String,
    val port: String,
    val license: String?
)