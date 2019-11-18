package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.SecurityPrincipal
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
}