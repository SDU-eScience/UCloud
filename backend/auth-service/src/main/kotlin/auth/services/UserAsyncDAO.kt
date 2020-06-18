package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import com.github.jasync.sql.db.util.length
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.bool
import dk.sdu.cloud.service.db.async.bytesArray
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.int
import dk.sdu.cloud.service.db.async.long
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.timestamp
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.service.db.criteria
import dk.sdu.cloud.service.db.get
import io.ktor.http.HttpStatusCode
import org.hibernate.annotations.NaturalId
import org.joda.time.DateTimeZone
import org.joda.time.LocalDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Inheritance
import javax.persistence.InheritanceType
import javax.persistence.Table
import javax.persistence.Temporal
import javax.persistence.TemporalType


data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)
data class UserIdAndName(val userId: String, val firstNames: String)


/**
 * Updated in:
 *
 * - V1__Initial.sql
 * - V7__WayfId.sql
 */
object PrincipalTable : SQLTable("principals") {
    val type = text("dtype", notNull = true)
    val id = text("id", notNull = true)
    val role = text("role", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val uid = long("uid", notNull = true)
    val firstNames = text("first_names")
    val lastName = text("last_name")
    val orcId = text("orc_id")
    val phoneNumber = text("phone_number")
    val title = text("title")
    val hashedPassword = bytesArray("hashed_password")
    val salt = bytesArray("salt")
    val orgId = text("org_id")
    val wayfId = text("wayf_id")
    val email = text("email")
    val serviceLicenseAgreement = int("service_license_agreement", notNull = true)
    val wantsEmails = bool("wants_emails")
}

enum class USERTYPE {
    SERVICE,
    WAYF,
    PASSWORD
}

fun RowData.toPrincipal(totpStatus: Boolean) : Principal{
    when {
        getField(PrincipalTable.type).contains(USERTYPE.SERVICE.name) -> {
            return ServicePrincipal(
                getField(PrincipalTable.id),
                Role.valueOf(getField(PrincipalTable.role)),
                getField(PrincipalTable.uid)
            )
        }
        getField(PrincipalTable.type).contains(USERTYPE.WAYF.name) -> {
            return Person.ByWAYF(
                getField(PrincipalTable.id),
                Role.valueOf(getField(PrincipalTable.role)),
                getField(PrincipalTable.title),
                getField(PrincipalTable.firstNames),
                getField(PrincipalTable.lastName),
                getField(PrincipalTable.phoneNumber),
                getField(PrincipalTable.orcId),
                getField(PrincipalTable.email),
                getField(PrincipalTable.uid),
                getField(PrincipalTable.serviceLicenseAgreement),
                getField(PrincipalTable.wantsEmails),
                getField(PrincipalTable.orgId),
                getField(PrincipalTable.wayfId)
            )
        }
        getField(PrincipalTable.type).contains(USERTYPE.PASSWORD.name) -> {
            return Person.ByPassword(
                getField(PrincipalTable.id),
                Role.valueOf(getField(PrincipalTable.role)),
                getField(PrincipalTable.title),
                getField(PrincipalTable.firstNames),
                getField(PrincipalTable.lastName),
                getField(PrincipalTable.phoneNumber),
                getField(PrincipalTable.orcId),
                getField(PrincipalTable.email),
                getField(PrincipalTable.uid),
                totpStatus,
                getField(PrincipalTable.serviceLicenseAgreement),
                getField(PrincipalTable.wantsEmails),
                getField(PrincipalTable.hashedPassword),
                getField(PrincipalTable.salt)
            )
        }
        else -> {
            throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unknown Principal Type")
        }
    }
}


data class UserInformation(
    val email: String?,
    val firstNames: String?,
    val lastName: String?
)

class UserAsyncDAO(
    private val passwordHashingService: PasswordHashingService,
    private val twoFactorDAO: TwoFactorAsyncDAO
) {

    suspend fun getUserInfo(db: DBContext, username: String): UserInformation {
        val user = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        SELECT * 
                        FROM principals
                        WHERE id = ?username
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()
        }
        return UserInformation(
            user.getField(PrincipalTable.email),
            user.getField(PrincipalTable.firstNames),
            user.getField(PrincipalTable.lastName)
        )
    }

    suspend fun updateUserInfo(
        db: DBContext,
        username: String,
        firstNames: String?,
        lastName: String?,
        email: String?
    ) {
        db.withSession { session ->
            val user = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?username
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()

            session
                .sendPreparedStatement(
                    {
                        if (!firstNames.isNullOrBlank()) {
                            setParameter("firstNames", firstNames)
                        } else {
                            setParameter("firstNames", user.getField(PrincipalTable.firstNames))
                        }
                        if (!lastName.isNullOrBlank()) {
                            setParameter("lastName", lastName)
                        } else {
                            setParameter("lastName", user.getField(PrincipalTable.lastName))
                        }
                        if (!email.isNullOrBlank()) {
                            setParameter("email", email)
                        } else {
                            setParameter("email", user.getField(PrincipalTable.email))
                        }
                        setParameter("username", username)
                    },
                    """
                        UPDATE permissions
                        SET first_names = ?firstNames, last_name = ?lastName, email = ?email
                        WHERE id = ?username
                    """.trimIndent()
                )
        }
    }

    suspend fun findById(db: DBContext, id: String): Principal {
        val status = twoFactorDAO.findStatusBatched(db, listOf(id))
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull()?.toPrincipal(status.getValue(id)) ?: throw UserException.NotFound()
        }
    }

    suspend fun findByIdOrNull(db: DBContext, id: String): Principal? {
        val status = twoFactorDAO.findStatusBatched(db, listOf(id))
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull()?.toPrincipal(status.getValue(id))
        }
    }

    suspend fun findAllByIds(db: DBContext, ids: List<String>): Map<String, Principal?> {
        val status = twoFactorDAO.findStatusBatched(db, ids)
        val usersWeFound = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("ids", ids)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id IN (select unnest(?ids::text[]))
                    """.trimIndent()
                ).rows
                .map { rowData ->
                rowData.toPrincipal(status.getValue(rowData.getField(PrincipalTable.id)))
                }
                .associateBy { principal ->
                    principal.id
                }
        }

        val usersWeDidntFind = ids.filter { it !in usersWeFound }
        val nullEntries = usersWeDidntFind.map { it to null as Principal? }.toMap()

        return usersWeFound + nullEntries
    }

    suspend fun findEmail(db: DBContext, id: String): String? {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull()?.getField(PrincipalTable.email)
        }
    }

    suspend fun findByEmail(db: DBContext, email: String): UserIdAndName {
        val user = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("email", email)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE email = ?email
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()
        }

        return UserIdAndName(user.getField(PrincipalTable.id), user.getField(PrincipalTable.firstNames))
    }

    suspend fun findAllByUIDs(db: DBContext, uids: List<Long>): Map<Long, Principal?> {
        val users = db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("uids", uids)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE uid IN (select unnest(?uids::text[]))
                    """.trimIndent()
                ).rows
        }

        val twoFactorStatus = twoFactorDAO.findStatusBatched(db, users.map { it.getField(PrincipalTable.id) })

        val usersWeFound = users
            .map { it.toPrincipal(twoFactorStatus.getValue(it.getField(PrincipalTable.id))) }
            .associateBy { it.uid }

        val usersWeDidntFind = uids.filter { it !in usersWeFound }
        val nullEntires = usersWeDidntFind.map { it to null as Principal? }.toMap()

        return usersWeFound + nullEntires
    }

    suspend fun findByUsernamePrefix(db: DBContext, prefix: String): List<Principal> {
        return db.withSession{ session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("prefix", "%$prefix")
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?prefix
                    """.trimIndent()
                ).rows.map { it.toPrincipal(false) }
        }
    }

    suspend fun findByWayfId(db: DBContext, wayfId: String): Person.ByWAYF {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("wayfId", wayfId)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE wayf_id = :wayfId
                    """.trimIndent()
                ).rows.singleOrNull()?.toPrincipal(false) as? Person.ByWAYF ?: throw UserException.NotFound()
        }
    }

    suspend fun findByWayfIdAndUpdateEmail(db: DBContext, wayfId: String, email: String?): Person.ByWAYF {
        val principal = db.withSession { session ->
            val principal = session
                .sendPreparedStatement(
                    {
                        setParameter("wayfId", wayfId)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE wayfId = ?wayfId
                    """.trimIndent()
                ).rows.firstOrNull()


            if (principal != null && email != null) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("email", email)
                            setParameter("wayfId", wayfId)
                        },
                        """
                            UPDATE principals
                            SET email = ?email
                            WHERE wayfId = ?wayfId
                        """.trimIndent()
                    )
            }
            principal
        }
        return principal?.toPrincipal(false) as? Person.ByWAYF ?: throw UserException.NotFound()
    }

    suspend fun insert(db: DBContext, principal: Principal) {
        db.withSession { session ->
            when (principal) {
                is Person.ByWAYF ->
                    session.insert(PrincipalTable) {
                        set(PrincipalTable.type, USERTYPE.WAYF.name)
                        set(PrincipalTable.id, principal.id)
                        set(PrincipalTable.role, principal.role.toString())
                        set(PrincipalTable.createdAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.modifiedAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.uid, principal.uid)
                        set(PrincipalTable.firstNames, principal.firstNames)
                        set(PrincipalTable.lastName, principal.lastName)
                        set(PrincipalTable.orcId, principal.orcId)
                        set(PrincipalTable.phoneNumber, principal.phoneNumber)
                        set(PrincipalTable.title, principal.title)
                        set(PrincipalTable.orgId, principal.organizationId)
                        set(PrincipalTable.wayfId, principal.wayfId)
                        set(PrincipalTable.email, principal.email)
                        set(PrincipalTable.serviceLicenseAgreement, 0)
                        set(PrincipalTable.wantsEmails, true)
                    }
                is Person.ByPassword ->
                    session.insert(PrincipalTable) {
                        set(PrincipalTable.type, USERTYPE.PASSWORD.name)
                        set(PrincipalTable.id, principal.id)
                        set(PrincipalTable.role, principal.role.toString())
                        set(PrincipalTable.createdAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.modifiedAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.title, principal.title)
                        set(PrincipalTable.firstNames, principal.firstNames)
                        set(PrincipalTable.lastName, principal.lastName)
                        set(PrincipalTable.phoneNumber, principal.phoneNumber)
                        set(PrincipalTable.orcId, principal.orcId)
                        set(PrincipalTable.uid, principal.uid)
                        set(PrincipalTable.email, principal.email)
                        set(PrincipalTable.serviceLicenseAgreement, principal.serviceLicenseAgreement)
                        set(PrincipalTable.wantsEmails, principal.wantsEmails)
                        set(PrincipalTable.hashedPassword, principal.password)
                        set(PrincipalTable.salt, principal.salt)
                    }

                is ServicePrincipal ->
                    session.insert(PrincipalTable) {
                        set(PrincipalTable.type, USERTYPE.SERVICE.name)
                        set(PrincipalTable.id, principal.id)
                        set(PrincipalTable.role, principal.role.toString())
                        set(PrincipalTable.createdAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.modifiedAt, LocalDateTime(DateTimeZone.UTC))
                        set(PrincipalTable.uid, principal.uid)
                    }
                else -> {
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "Unknown principal type")
                }
            }
        }
    }

    suspend fun delete(db: DBContext, id: String) {
        db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        DELETE from principals
                        WHERE id = ?id
                    """.trimIndent()
                )
        }
    }

    suspend fun updatePassword(
        db: DBContext,
        id: String,
        newPassword: String,
        currentPasswordForVerification: String?
    ) {
        db.withSession { session ->
            val principal = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()
            if (!principal.getField(PrincipalTable.type).contains(USERTYPE.PASSWORD.name)) {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "User is not Password but: ${principal.getField(PrincipalTable.type)}")
            }
            if (currentPasswordForVerification != null) {
                if (!passwordHashingService.checkPassword(
                        principal.getField(PrincipalTable.hashedPassword),
                        principal.getField(PrincipalTable.salt),
                        currentPasswordForVerification
                    )
                ) {
                    throw UserException.InvalidAuthentication()
                }
            }
            val (hashedPassword, salt) = passwordHashingService.hashPassword(newPassword)
            session
                .sendPreparedStatement(
                    {
                        setParameter("hashed", hashedPassword)
                        setParameter("salt", salt)
                        setParameter("id", id)
                    },
                    """
                        UPDATE principals
                        SET hashed_password = ?hashed, salt = ?salt
                        WHERE id = ?id
                    """.trimIndent()
                )
        }
    }

    suspend fun unconditionalUpdatePassword(db: DBContext, id: String, newPassword: String) {
        db.withSession { session ->
            val principal = session
                .sendPreparedStatement(
                    {
                        setParameter("id", id)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()
            if (!principal.getField(PrincipalTable.type).contains(USERTYPE.PASSWORD.name)) {
                throw RPCException.fromStatusCode(
                    HttpStatusCode.BadRequest,
                    "User is not Password but: ${principal.getField(PrincipalTable.type)}"
                )
            }
            val (hashedPassword, salt) = passwordHashingService.hashPassword(newPassword)
            session
                .sendPreparedStatement(
                    {
                        setParameter("hashed", hashedPassword)
                        setParameter("salt", salt)
                        setParameter("id", id)
                    },
                    """
                        UPDATE principals
                        SET hashed_password = ?hashed, salt = ?salt
                        WHERE id = ?id
                    """.trimIndent()
                )
        }
    }

    suspend fun setAcceptedSlaVersion(db: DBContext, user: String, version: Int) {
        db.withSession { session ->
            val principal = session
                .sendPreparedStatement(
                    {
                        setParameter("id", user)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()

            session
                .sendPreparedStatement(
                    {
                        setParameter("sla", version)
                        setParameter("id", user)
                    },
                    """
                        UPDATE principals
                        SET service_license_agreement = ?sla
                        WHERE id = ?id
                    """.trimIndent()
                )
        }
    }

    //Should be moved out of AUTH in case of expanding functionality of subscriptions
    suspend fun toggleEmail(db: DBContext, username: String) {
        db.withSession { session ->
            val user = session
                .sendPreparedStatement(
                    {
                        setParameter("username", username)
                    },
                    """
                        SELECT * 
                        FROM principals
                        WHERE id = ?username
                    """.trimIndent()
                ).rows.singleOrNull() ?: throw UserException.NotFound()

            val wantsEmails = user.getField(PrincipalTable.wantsEmails)
            //WantEmails can be null even is IDE says it can't.
            if (wantsEmails != null) {
                session
                    .sendPreparedStatement(
                        {
                            setParameter("wantEmails", !wantsEmails)
                            setParameter("username", username)
                        },
                        """
                            UPDATE principals 
                            SET want_emails = ?wantEmails
                            WHERE id = ?username
                        """.trimIndent()
                    )
            } else {
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound, "User not found")
            }
        }
    }
    //Should be moved out of AUTH in case of expanding functionality of subscriptions
    suspend fun wantEmails(db: DBContext, username: String): Boolean {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("id", username)
                    },
                    """
                        SELECT *
                        FROM principals
                        WHERE id = ?id
                    """.trimIndent()
                ).rows.singleOrNull()?.getField(PrincipalTable.wantsEmails) ?: throw UserException.NotFound()
        }
    }
}

