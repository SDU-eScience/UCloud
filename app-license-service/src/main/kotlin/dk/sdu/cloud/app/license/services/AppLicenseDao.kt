package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.LicenseServer
import dk.sdu.cloud.app.license.api.LicenseServerId
import dk.sdu.cloud.app.license.api.LicenseServerWithId

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
        tags: List<String>,
        accessEntity: AccessEntity
    ): List<LicenseServerId>?

    fun listAll(
        session: Session,
        user: SecurityPrincipal
    ): List<LicenseServerWithId>?

    fun update(
        session: Session,
        appLicenseServer: LicenseServerWithId
    )

    fun delete(
        session: Session,
        serverId: String
    )

    fun addTag(
        session: Session,
        name: String,
        serverId: String
    )

    fun listTags(
        session: Session,
        serverId: String
    ): List<String>

    fun deleteTag(
        session: Session,
        name: String,
        serverId: String
    )
}

