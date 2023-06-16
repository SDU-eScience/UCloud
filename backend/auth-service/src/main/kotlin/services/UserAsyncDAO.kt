package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ProviderPrincipal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.*
import dk.sdu.cloud.service.timestampToLocalDateTime

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)
data class UserIdAndName(val userId: String, val firstNames: String, val lastName: String)

object PrincipalTable : SQLTable("principals") {
    val type = text("dtype", notNull = true)
    val id = text("id", notNull = true)
    val role = text("role", notNull = true)
    val createdAt = timestamp("created_at", notNull = true)
    val modifiedAt = timestamp("modified_at", notNull = true)
    val firstNames = text("first_names")
    val lastName = text("last_name")
    val title = text("title")
    val hashedPassword = byteArray("hashed_password")
    val salt = byteArray("salt")
    val email = text("email")
    val serviceLicenseAgreement = int("service_license_agreement", notNull = true)
}

enum class UserType {
    SERVICE,
    PERSON,
}

fun RowData.toPrincipal(totpStatus: Boolean): Principal {
    when {
        getField(PrincipalTable.type).contains(UserType.SERVICE.name) -> {
            return ServicePrincipal(
                getField(PrincipalTable.id),
                Role.valueOf(getField(PrincipalTable.role))
            )
        }

        getField(PrincipalTable.type).contains(UserType.PERSON.name) -> {
            return Person(
                getField(PrincipalTable.id),
                Role.valueOf(getField(PrincipalTable.role)),
                getField(PrincipalTable.title),
                getField(PrincipalTable.firstNames),
                getField(PrincipalTable.lastName),
                getFieldNullable(PrincipalTable.email),
                getField(PrincipalTable.serviceLicenseAgreement),
                totpStatus,
                emptyList(),
                getFieldNullable(PrincipalTable.hashedPassword),
                getFieldNullable(PrincipalTable.salt),
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
        val principal = findById(db, username)
        return when (principal) {
            is Person -> {
                UserInformation(
                    principal.email,
                    principal.firstNames,
                    principal.lastName
                )
            }

            else -> UserInformation(null, null, null)
        }
    }

    suspend fun findById(db: DBContext, id: String): Principal {
        return findByIdOrNull(db, id) ?: throw UserException.NotFound()
    }

    suspend fun findByIdOrNull(db: DBContext, id: String): Principal? {
        return findAllByIds(db, listOf(id))[id]
    }

    /**
     * Finds a set of [Principal]s by their [Principal.id] defined in [ids]
     */
    suspend fun findAllByIds(db: DBContext, ids: List<String>): Map<String, Principal?> {
        val status = twoFactorDAO.findStatusBatched(db, ids)
        val usersWeFound = db.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("ids", ids) },
                    """
                        select *
                        from auth.principals
                        where id = some(:ids::text[])
                    """
                ).rows
                .map { it.toPrincipal(status.getValue(it.getField(PrincipalTable.id))) }
                .associateBy { principal -> principal.id }
        }
        val usersWeDidntFind = ids.filter { it !in usersWeFound }
        val nullEntries = usersWeDidntFind.associateWith { null }
        return usersWeFound + nullEntries
    }

    suspend fun findEmail(db: DBContext, id: String): String? {
        return runCatching { getUserInfo(db, id).email }.getOrNull()
    }

    /**
     * Finds a [Person] by an [email]
     */
    suspend fun findByEmail(db: DBContext, email: String): UserIdAndName {
        val user = db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("email", email) },
                """
                    select *
                    from auth.principals
                    where email = :email
                """
            )
            .rows
            .singleOrNull() ?: throw UserException.NotFound()
        }

        return UserIdAndName(
            user.getField(PrincipalTable.id),
            user.getField(PrincipalTable.firstNames),
            user.getField(PrincipalTable.lastName)
        )
    }

    /**
     * Finds all [Principal]s by [prefix]. This is used for username generation.
     */
    suspend fun findByUsernamePrefix(db: DBContext, prefix: String): List<Principal> {
        return db.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("prefix", "$prefix%")
                    },
                    """
                        select *
                        from auth.principals
                        where id like :prefix
                    """
                )
                .rows
                .map { it.toPrincipal(false) }
        }
    }

    suspend fun findByExternalIdentityOrNull(
        idp: Int,
        identity: String,

        updatedEmail: String? = null,
        ctx: DBContext,
    ): Person? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("idp", idp)
                    setParameter("identity", identity)
                    setParameter("updated_email", updatedEmail)
                },
                """
                    with connected as (
                        select conn.principal
                        from auth.idp_connections conn
                        where
                            conn.idp = :idp
                            and conn.provider_identity = :identity
                    )
                    update auth.principals p
                    set email = coalesce(:updated_email::text, p.email)
                    from connected c
                    where c.principal = p.uid
                    returning p.*
                """
            ).rows.singleOrNull()?.toPrincipal(TODO()) as? Person?
        }
    }

    suspend fun insert(db: DBContext, principal: Principal) {
        db.withSession(remapExceptions = true) { session ->
            when (principal) {
                is Person -> {
                    session.sendPreparedStatement(
                        {
                            setParameter("id", principal.id)
                            setParameter("role", principal.role.name)
                            setParameter("first_names", principal.firstNames)
                            setParameter("last_name", principal.lastName)
                            setParameter("hashed_password", principal.password)
                            setParameter("salt", principal.salt)
                            setParameter("org_id", principal.organizationId)
                            setParameter("email", principal.email)
                        },
                        """
                           insert into auth.principals (dtype, id, role, first_names, last_name, hashed_password, salt, org_id, email)  
                           values ('USER', :id, :role, :first_names, :last_name, :hashed_password, :salt, :org_id, :email)  
                        """
                    )
                }

                is ProviderPrincipal -> {
                    error("Cannot insert ProviderPrincipals through this function")
                }

                is ServicePrincipal -> {
                    session.sendPreparedStatement(
                        { setParameter("id", principal.id) },
                        """
                            insert into auth.principals (dtype, id, role)
                            values ('SERVICE', :id, 'SERVICE')
                        """
                    )
                }
            }
        }
    }

    suspend fun updatePassword(
        db: DBContext,
        username: String,
        newPassword: String,
        conditionalChange: Boolean = true,
        currentPasswordForVerification: String? = null,
    ) {
        require(!conditionalChange || currentPasswordForVerification != null)

        db.withSession { session ->
            fun cannotChangePassword(): Nothing = throw RPCException(
                "Cannot change password for this user",
                HttpStatusCode.BadRequest
            )

            val currentPasswordAndSalt = session.sendPreparedStatement(
                { setParameter("username", username) },
                """
                    select p.hashed_password, p.salt
                    from auth.principals p
                    where p.id = :username
                """
            ).rows.singleOrNull()?.let { row ->
                row.getAs<ByteArray?>(0) to row.getAs<ByteArray?>(1)
            } ?: cannotChangePassword()

            val currentPassword = currentPasswordAndSalt.first ?: cannotChangePassword()
            val currentSalt = currentPasswordAndSalt.second ?: cannotChangePassword()

            if (conditionalChange) {
                val isValidPassword = passwordHashingService.checkPassword(
                    currentPassword,
                    currentSalt,
                    currentPasswordForVerification ?:
                        throw RPCException("No password supplied", HttpStatusCode.BadRequest)
                )

                if (!isValidPassword) throw UserException.InvalidAuthentication()
            }

            val (newPasswordHash, newSalt) = passwordHashingService.hashPassword(newPassword)
            session.sendPreparedStatement(
                {
                    setParameter("hashed", newPasswordHash)
                    setParameter("salt", newSalt)
                    setParameter("id", username)
                },
                """
                    update auth.principals
                    set
                        hashed_password = :hashed,
                        salt = :salt
                    where
                        id = :id
                """
            )
        }
    }

    suspend fun setAcceptedSlaVersion(db: DBContext, user: String, version: Int) {
        db.withSession { session ->
            val success = session
                .sendPreparedStatement(
                    {
                        setParameter("sla", version)
                        setParameter("id", user)
                    },
                    """
                        update auth.principals
                        set service_license_agreement = :sla
                        where id = :id
                    """
                )
                .rowsAffected >= 1

            if (!success) throw UserException.NotFound()
        }
    }
}
