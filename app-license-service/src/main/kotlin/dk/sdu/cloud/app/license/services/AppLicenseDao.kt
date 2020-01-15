package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.LicenseServer
import dk.sdu.cloud.app.license.api.LicenseServerId
import dk.sdu.cloud.app.license.api.LicenseServerWithId
import dk.sdu.cloud.app.license.services.acl.UserEntity

interface AppLicenseDao<Session> {
    fun getById(
        session: Session,
        id: String
    ): LicenseServerWithId?

    fun create(
        session: Session,
        serverId: String,
        appLicenseServer: LicenseServer
    )

    fun list(
        session: Session,
        names: List<String>,
        entity: UserEntity
    ): List<LicenseServerId>?

    fun listAll(
        session: Session,
        user: SecurityPrincipal
    ): List<LicenseServerWithId>?

    fun update(
        session: Session,
        appLicenseServer: LicenseServerWithId
    )
}
