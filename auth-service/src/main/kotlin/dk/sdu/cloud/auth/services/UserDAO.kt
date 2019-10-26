package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)

interface UserDAO<Session> {
    fun findById(session: Session, id: String): Principal
    fun findByIdOrNull(session: Session, id: String): Principal?
    fun findAllByIds(session: Session, ids: List<String>): Map<String, Principal?>
    fun findAllByUIDs(session: Session, uids: List<Long>): Map<Long, Principal?>
    fun findByUsernamePrefix(session: Session, prefix: String): List<Principal>
    fun findByWayfId(session: Session, wayfId: String): Person.ByWAYF
    /**
     * Finds a user by WAYF id and updates the [email] if != null.
     *
     * This function is used primarily for backwards compatible reasons. It allows us to attach an email to old
     * accounts which didn't already have one. Previously WAYF wouldn't send us an email back now it does. Everything
     * is still written to support SAML endpoints that do not send us an email.
     */
    fun findByWayfIdAndUpdateEmail(session: Session, wayfId: String, email: String?): Person.ByWAYF
    fun insert(session: Session, principal: Principal)
    fun updatePassword(
        session: Session,
        id: String,
        newPassword: String,
        currentPasswordForVerification: String?
    )

    fun delete(session: Session, id: String)

    fun listAll(session: Session): List<Principal>
}

