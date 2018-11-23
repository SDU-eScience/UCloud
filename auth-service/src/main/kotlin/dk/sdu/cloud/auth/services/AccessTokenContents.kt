package dk.sdu.cloud.auth.services

import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.Principal

class AccessTokenContents(
    val user: Principal,
    val scopes: List<SecurityScope>,
    val createdAt: Long,
    val expiresAt: Long,

    val claimableId: String? = null,
    val sessionReference: String? = null,
    val extendedBy: String? = null
)
