package dk.sdu.cloud.auth.api

import com.fasterxml.jackson.annotation.JsonIgnore
import dk.sdu.cloud.SecurityScope

class AccessTokenContents(
    val user: Principal,
    scopes: List<SecurityScope>,
    val createdAt: Long,
    val expiresAt: Long?,

    val claimableId: String? = null,
    val sessionReference: String? = null,
    val extendedBy: String? = null
) {
    val scopes: List<String> = scopes.map { it.toString() }

    @get:JsonIgnore
    val scopesParsed: List<SecurityScope>
        get() = scopes.map { SecurityScope.parseFromString(it) }
}
