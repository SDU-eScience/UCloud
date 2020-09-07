package dk.sdu.cloud.grant.services

import dk.sdu.cloud.SecurityPrincipal
import dk.sdu.cloud.grant.api.UserCriteria

fun UserCriteria.matches(principal: SecurityPrincipal): Boolean {
    return when (this) {
        is UserCriteria.Anyone -> true
        is UserCriteria.EmailDomain -> principal.email?.substringAfter('@') == domain
        is UserCriteria.WayfOrganization -> principal.organization == org
    }
}
