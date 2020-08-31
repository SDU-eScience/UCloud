package dk.sdu.cloud.app.license.services

import dk.sdu.cloud.app.license.api.AccessEntity
import dk.sdu.cloud.app.license.api.ServerAccessRight

data class AccessEntityWithPermission(
    val entity: AccessEntity,
    val permission: ServerAccessRight
)
