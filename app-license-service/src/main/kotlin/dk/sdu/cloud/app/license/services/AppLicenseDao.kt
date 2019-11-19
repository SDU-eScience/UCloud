package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.ApplicationLicenseServer

interface AppLicenseDao<Session> {
    fun getById(
        session: Session,
        id: String
    ): ApplicationLicenseServerEntity?

    fun create(
        session: Session,
        appLicenseServer: ApplicationLicenseServer
    ) : String

    fun save(
        session: Session,
        appLicenseServer: ApplicationLicenseServer,
        withId: String
    )
}