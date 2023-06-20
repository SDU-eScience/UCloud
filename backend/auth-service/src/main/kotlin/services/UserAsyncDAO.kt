package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.IdentityProviderConnection
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ProviderPrincipal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.db.async.*

data class HashedPasswordAndSalt(val hashedPassword: ByteArray, val salt: ByteArray)
data class UserIdAndName(val userId: String, val firstNames: String, val lastName: String)

enum class UserType {
    SERVICE,
    PERSON,
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
        val principal = findByUsername(db, username)
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

    suspend fun findByUsername(db: DBContext, id: String): Principal {
        return findByUsernameOrNull(db, id) ?: throw UserException.NotFound()
    }

    suspend fun findByUsernameOrNull(db: DBContext, id: String): Principal? {
        return findAllByUsername(db, listOf(id))[id]
    }

    /**
     * Finds a set of [Principal]s by their [Principal.id] defined in [ids]
     */
    suspend fun findAllByUsername(db: DBContext, ids: List<String>): Map<String, Principal?> {
        val builder = HashMap<String, Principal?>()
        ids.forEach { builder[it] = null }

        db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("ids", ids) },
                """
                    with
                        two_factor_enabled as (
                            select creds.principal_id
                            from auth.two_factor_credentials creds
                            where
                                creds.enforced = true
                                and creds.principal_id = some(:ids::text[])
                        ),
                        connections as (
                            select
                                p.uid,
                                array_agg(idp.id) as idps,
                                array_agg(conn.provider_identity) as connections,
                                array_agg(idp.counts_as_multi_factor) as is_multi_factor,
                                array_agg(conn.organization_id) as org_id
                            from
                                auth.principals p
                                left join auth.idp_connections conn on p.uid = conn.principal
                                left join auth.identity_providers idp on conn.idp = idp.id
                            group by p.uid
                        )
                    select
                        p.id,
                        p.role,
                        p.dtype,

                        p.first_names,
                        p.last_name,
                        p.email,
                        p.service_license_agreement,
                        p.org_id,

                        p.hashed_password,
                        p.salt,

                        c.idps,
                        c.connections,
                        c.org_id,
                        coalesce(mfa.principal_id is not null or true = some(c.is_multi_factor), false) as mfa_enabled
                    from
                        auth.principals p
                        join connections c on p.uid = c.uid
                        left join two_factor_enabled mfa on p.id = mfa.principal_id
                    where
                        p.id = some(:ids::text[]);

                        select true = some(array[]::bool[])
                """
            ).rows.forEach { row ->
                val id = row.getString(0)!!
                val role = Role.valueOf(row.getString(1)!!)
                val dtype = UserType.valueOf(row.getString(2)!!)

                when (dtype) {
                    UserType.SERVICE -> {
                        builder[id] = ServicePrincipal(id, role)
                    }

                    UserType.PERSON -> {
                        val firstNames = row.getString(3)!!
                        val lastName = row.getString(4)!!
                        val email = row.getString(5)!!
                        val sla = row.getInt(6)!!
                        val orgId = row.getString(7)
                        val hashedPassword = row.getAs<ByteArray?>(8)
                        val salt = row.getAs<ByteArray?>(9)

                        val idps = row.getAs<List<Int?>>(10)
                        val idpIdentities = row.getAs<List<String?>>(11)
                        val orgIds = row.getAs<List<String?>>(12)
                        val mfaEnabled = row.getBoolean(13)!!

                        Person(
                            id,
                            role,
                            firstNames,
                            lastName,
                            email,
                            sla,
                            orgId,
                            mfaEnabled,
                            idps.indices.mapNotNull { idx ->
                                val idp = idps[idx] ?: return@mapNotNull null
                                val identity = idpIdentities[idx] ?: return@mapNotNull null
                                val idpOrg = orgIds[idx]
                                IdentityProviderConnection(idp, identity, idpOrg)
                            },
                            hashedPassword,
                            salt
                        )
                    }
                }
            }
        }
        return builder
    }

    suspend fun findEmail(db: DBContext, id: String): String? {
        return runCatching { getUserInfo(db, id).email }.getOrNull()
    }

    /**
     * Finds a [Person] by an [email]
     */
    suspend fun findByEmail(db: DBContext, email: String): UserIdAndName {
        val user = db.withSession { session ->
            val username = session.sendPreparedStatement(
                { setParameter("email", email) },
                """
                    select id
                    from auth.principals
                    where email = :email
                """
            )
            .rows
            .singleOrNull()
            ?.getString(0) ?: throw UserException.NotFound()

            findByUsername(session, username)
        }

        val person = user as? Person

        return UserIdAndName(
            user.id,
            person?.firstNames ?: "Unknown",
            person?.lastName ?: "Unknown",
        )
    }

    suspend fun findUsernamesByPrefix(db: DBContext, prefix: String): List<String> {
        return db.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("prefix", "$prefix%") },
                """
                    select id
                    from auth.principals
                    where id like :prefix
                """
            )
            .rows
            .map { it.getString(0)!! }
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

    // TODO The insertion API doesn't really make sense. I don't think it should be accepting a Principal in this case.
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
