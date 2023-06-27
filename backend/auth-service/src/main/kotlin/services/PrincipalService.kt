package dk.sdu.cloud.auth.services

import dk.sdu.cloud.ActorAndProject
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.IdentityProviderConnection
import dk.sdu.cloud.auth.api.OptionalUserInformation
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.auth.api.ServicePrincipal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.safeUsername
import dk.sdu.cloud.service.db.async.*
import java.security.SecureRandom
import java.util.*
import kotlin.collections.HashMap

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

class PrincipalService(
    private val passwordHashingService: PasswordHashingService,
    private val db: DBContext,
    private val serviceClient: AuthenticatedClient,
) {
    suspend fun getUserInfo(
        username: String,
        ctx: DBContext = db,
    ): UserInformation {
        val principal = findByUsername(username, ctx)
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

    suspend fun findByUsername(
        id: String,
        ctx: DBContext = db,
    ): Principal {
        return findByUsernameOrNull(id, ctx) ?: throw UserException.NotFound()
    }

    suspend fun findByUsernameOrNull(
        id: String,
        ctx: DBContext = db,
    ): Principal? {
        return findAllByUsername(listOf(id), ctx)[id]
    }

    suspend fun findAllByUsername(
        ids: List<String>,
        ctx: DBContext = db,
    ): Map<String, Principal?> {
        val builder = HashMap<String, Principal?>()
        ids.forEach { builder[it] = null }

        ctx.withSession { session ->
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
                        p.id = some(:ids::text[])
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

                        builder[id] = Person(
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

    suspend fun findEmailByUsernameOrNull(
        id: String,
        ctx: DBContext = db,
    ): String? {
        return runCatching { getUserInfo(id, ctx).email }.getOrNull()
    }

    suspend fun findByEmail(
        email: String,
        ctx: DBContext = db
    ): UserIdAndName {
        val user = ctx.withSession { session ->
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

            findByUsername(username, session)
        }

        val person = user as? Person

        return UserIdAndName(
            user.id,
            person?.firstNames ?: "Unknown",
            person?.lastName ?: "Unknown",
        )
    }

    suspend fun findUsernamesByPrefix(
        prefix: String,
        ctx: DBContext = db,
    ): List<String> {
        return ctx.withSession { session ->
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

    suspend fun findByIdpAndTrackInfo(
        idp: Int,
        identity: String,

        firstNames: String? = null,
        lastName: String? = null,
        updatedEmail: String? = null,
        organization: String? = null,
        ctx: DBContext = db,
    ): Person? {
        // NOTE(Dan): We no longer automatically change the email. We do this because the end-user can manually change
        // their email, and we do not want to override the change everytime they log in. If they need to change the
        // email address, then they can just change it.

        return ctx.withSession { session ->
            trackIdpInfo(idp, identity, firstNames, lastName, updatedEmail, organization, session)

            val username = session.sendPreparedStatement(
                {
                    setParameter("idp", idp)
                    setParameter("identity", identity)
                },
                """
                    select p.id
                    from
                        auth.idp_connections conn join
                        auth.principals p on conn.principal = p.uid
                    where
                        conn.idp = :idp
                        and conn.provider_identity = :identity
                """
            ).rows.singleOrNull()?.getString(0) ?: return@withSession null

            findByUsernameOrNull(username, session) as? Person?
        }
    }

    private suspend fun trackIdpInfo(
        idp: Int,
        identity: String,

        firstNames: String? = null,
        lastName: String? = null,
        email: String? = null,
        organization: String? = null,
        ctx: DBContext = db,
    ) {
        // NOTE(Dan): This was added after a discussion with Claudio. Since we now allow end-users to change their
        // contact information, then we want to also be able to keep a record of info from the IdP. We want to do
        // this because we might want to cross-reference the information between the two if any ambiguity arises
        // related to a support situation.
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("idp", idp)
                    setParameter("idp_identity", identity)
                    setParameter("first_names", firstNames)
                    setParameter("last_name", lastName)
                    setParameter("email", email)
                    setParameter("organization_id", organization)
                },
                """
                    with
                        connected_user as (
                            select c.principal
                            from
                                auth.idp_connections c
                            where
                                c.idp = :idp
                                and c.provider_identity = :idp_identity
                        ),
                        latest_response as (
                            select
                                r.first_names,
                                r.last_name,
                                r.organization_id,
                                r.email
                            from
                                auth.idp_auth_responses r join
                                connected_user p on r.associated_user = p.principal
                            order by r.created_at desc
                            limit 1
                        ),
                        row_to_insert as (
                            select
                                :first_names::text first_names,
                                :last_name::text last_name,
                                :organization_id::text organization_id,
                                :email::text email
                        )
                    insert into auth.idp_auth_responses(associated_user, idp, idp_identity, first_names, last_name, 
                        organization_id, email)
                    select u.principal, :idp, :idp_identity, i.first_names, i.last_name, i.organization_id, i.email
                    from
                        row_to_insert i,
                        connected_user u
                    where
                        not exists(
                            select 1
                            from latest_response r
                            where
                                i.first_names is not distinct from r.first_names
                                and i.last_name is not distinct from r.last_name
                                and i.organization_id is not distinct from r.organization_id
                                and i.email is not distinct from r.email
                        );
                """
            )
        }
    }

    suspend fun insert(
        id: String,
        role: Role,
        type: UserType,

        firstNames: String? = null,
        lastName: String? = null,

        organizationId: String? = null,
        email: String? = null,

        hashedPassword: ByteArray? = null,
        salt: ByteArray? = null,

        connections: List<IdentityProviderConnection> = emptyList(),

        ctx: DBContext = db,
    ): Int {
        when (type) {
            UserType.SERVICE -> {
                require(role == Role.SERVICE)
                require(firstNames == null)
                require(lastName == null)
                require(organizationId == null)
                require(email == null)
                require(hashedPassword == null)
                require(salt == null)
            }

            UserType.PERSON -> {
                require(role != Role.SERVICE)
                require(firstNames != null)
                require(lastName != null)
                require(email != null)
            }
        }

        return ctx.withSession(remapExceptions = true) { session ->
            val uid = session.sendPreparedStatement(
                {
                    setParameter("id", id)
                    setParameter("role", role.name)
                    setParameter("dtype", type.name)
                    setParameter("first_names", firstNames)
                    setParameter("last_name", lastName)
                    setParameter("hashed_password", hashedPassword)
                    setParameter("salt", salt)
                    setParameter("org_id", organizationId)
                    setParameter("email", email)
                },
                """
                   insert into auth.principals (dtype, id, role, first_names, last_name, hashed_password, salt, org_id, email)  
                   values (:dtype, :id, :role, :first_names, :last_name, :hashed_password, :salt, :org_id, :email)  
                   returning uid
                """
            ).rows.single().getInt(0)!!

            insertConnections(uid, connections, session)

            uid
        }
    }

    suspend fun insertConnections(
        uid: Int,
        connections: List<IdentityProviderConnection>,
        ctx: DBContext = db,
    ) {
        if (connections.isEmpty()) return

        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("principal", uid)
                    setParameter("idps", connections.map { it.identityProvider })
                    setParameter("provider_identities", connections.map { it.identity })
                    setParameter("org_ids", connections.map { it.organizationId })
                },
                """
                    insert into auth.idp_connections(principal, idp, provider_identity, organization_id) 
                    select :principal, unnest(:idps::int[]), unnest(:provider_identities::text[]), unnest(:org_ids::text[])
                """
            )
        }
    }

    suspend fun updatePassword(
        username: String,
        newPassword: String,
        conditionalChange: Boolean = true,
        currentPasswordForVerification: String? = null,
        ctx: DBContext = db,
    ) {
        require(!conditionalChange || currentPasswordForVerification != null)

        ctx.withSession { session ->
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

    suspend fun setAcceptedSlaVersion(
        user: String,
        version: Int,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
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

    private val secureRandom = SecureRandom()
    private fun generateToken(): String {
        val array = ByteArray(64)
        secureRandom.nextBytes(array)
        return Base64.getEncoder().encodeToString(array)
    }

    suspend fun updateUserInfo(
        remoteHost: String,

        username: String,
        firstNames: String?,
        lastName: String?,
        email: String?,
        ctx: DBContext = db,
    ) {
        if (email != null && !emailLooksValid(email)) {
            throw RPCException("This email does not look valid. Please try again.", HttpStatusCode.BadRequest)
        }

        val token = generateToken()
        val recipientEmail = ctx.withSession { session ->
            val success = session.sendPreparedStatement(
                { setParameter("remote_host", remoteHost) },
                """
                    with count as (
                        select count(*) total
                        from auth.verification_email_log
                        where ip_address = :remote_host
                    )
                    insert into auth.verification_email_log(ip_address) 
                    select :remote_host
                    from count
                    where count.total < 10
                """
            ).rowsAffected >= 1

            if (!success) {
                throw RPCException(
                    "You have made too many requests recently. Try again later.",
                    HttpStatusCode.TooManyRequests
                )
            }

            session.sendPreparedStatement(
                {
                    setParameter("username", username)
                    setParameter("first_names", firstNames)
                    setParameter("last_name", lastName)
                    setParameter("email", email)
                    setParameter("token", token)
                },
                """
                    with
                        user_info as (
                            select uid, email
                            from auth.principals
                            where id = :username
                        ),
                        insertion as (
                            insert into auth.user_info_update_request (uid, first_names, last_name, email, verification_token)
                            select uid, :first_names::text, :last_name::text, :email::text, :token::text
                            from user_info i
                        )
                    select coalesce(:email::text, i.email)
                    from user_info i;
                """
            ).rows.singleOrNull()?.getString(0)
        } ?: throw RPCException(
            "Found no email. Re-try by setting an email or contact support.",
            HttpStatusCode.BadRequest
        )

        Mails.sendDirect.call(
            bulkRequestOf(
                SendDirectMandatoryEmailRequest(
                    recipientEmail,
                    Mail.VerifyEmailAddress(
                        verifyType = "info-update",
                        token = token,
                        subject = "[UCloud] Someone has requested a change to your UCloud account",
                        username = username
                    )
                )
            ),
            serviceClient
        ).orThrow()
    }

    suspend fun verifyUserInfoUpdate(
        token: String,
        ctx: DBContext = db,
    ): Boolean {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("token", token) },
                """
                    with update_info as (
                        update auth.user_info_update_request
                        set
                            confirmed = true,
                            modified_at = now()
                        where
                            verification_token = :token
                            and not confirmed
                            and now() - created_at < '24 hours'::interval
                        returning 
                            uid,
                            first_names,
                            last_name,
                            email
                    )
                    update auth.principals p
                    set
                        first_names = coalesce(i.first_names, p.first_names),
                        last_name = coalesce(i.last_name, p.last_name),
                        email = coalesce(i.email, p.email)
                    from
                        update_info i
                    where
                        i.uid = p.uid
                """
            ).rowsAffected >= 1
        }
    }

    suspend fun retrieveOptionalUserInfo(
        actorAndProject: ActorAndProject,
        ctx: DBContext = db,
    ): OptionalUserInformation {
        return ctx.withSession { session ->
            val row = session.sendPreparedStatement(
                { setParameter("username", actorAndProject.actor.safeUsername()) },
                """
                    select
                        info.organization_full_name,
                        info.department,
                        info.research_field,
                        info.position
                    from
                        auth.principals p join
                        auth.additional_user_info info on p.uid = info.associated_user
                    where
                        p.id = :username and
                        p.dtype = 'PERSON'
                """
            ).rows.firstOrNull()

            if (row == null) {
                OptionalUserInformation()
            } else {
                OptionalUserInformation(
                    row.getString(0),
                    row.getString(1),
                    row.getString(2),
                    row.getString(3),
                )
            }
        }
    }

    suspend fun updateOptionalUserInfo(
        actorAndProject: ActorAndProject,
        info: OptionalUserInformation,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", actorAndProject.actor.safeUsername())
                    setParameter("organization_full_name", info.organizationFullName)
                    setParameter("department", info.department)
                    setParameter("research_field", info.researchField)
                    setParameter("position", info.position)
                },
                """
                    insert into auth.additional_user_info (associated_user, organization_full_name, department,
                        research_field, position) 
                    select p.uid, :organization_full_name, :department, :research_field, :position
                    from auth.principals p
                    where p.id = :username
                    on conflict (associated_user) do update set
                        organization_full_name = excluded.organization_full_name,
                        department = excluded.department,
                        research_field = excluded.research_field,
                        position = excluded.position
                """
            )
        }
    }
}

sealed class UserException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyExists : UserException("User already exists", HttpStatusCode.Conflict)
    class NotFound : UserException("User not found", HttpStatusCode.NotFound)
    class InvalidAuthentication : UserException("Invalid username or password", HttpStatusCode.BadRequest)
}

