package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.Role
import dk.sdu.cloud.auth.api.IdentityProviderConnection
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Registration
import dk.sdu.cloud.auth.http.LoginResponder
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.calls.client.urlEncode
import dk.sdu.cloud.calls.server.CallHandler
import dk.sdu.cloud.calls.server.HttpCall
import dk.sdu.cloud.calls.server.remoteHost
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import dk.sdu.cloud.toReadableStacktrace
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicLong

data class InternalRegistration(
    val sessionId: String,
    val firstNames: String?,
    val lastName: String?,
    val email: String?,
    val emailVerified: Boolean,
    val organization: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val emailVerificationToken: String?,
    val idp: Int,
    val idpIdentity: String,

    // The following properties are optional for the registration and not validated by us:
    val organizationFullName: String? = null,
    val department: String? = null,
    val researchField: String? = null,
    val position: String? = null,
) {
    fun toApiModel(): Registration = Registration(
        sessionId,
        firstNames,
        lastName,
        email,
        organizationFullName,
        department,
        researchField,
        position
    )
}

class RegistrationService(
    private val db: DBContext,
    private val loginResponder: LoginResponder,
    private val serviceClient: AuthenticatedClient,
    private val users: PrincipalService,
    private val usernameGenerator: UniqueUsernameService,
    private val backgroundScope: BackgroundScope,
) {
    private val nextCleanup = AtomicLong(0)

    init {
        launchCleanupJob()
    }

    private fun mapRow(row: RowData): InternalRegistration {
        return InternalRegistration(
            row.getString(0)!!,
            row.getString(1),
            row.getString(2),
            row.getString(3),
            row.getBoolean(4)!!,
            row.getString(5),
            row.getLong(6)!!,
            row.getLong(7)!!,
            row.getString(8),
            row.getInt(9)!!,
            row.getString(10)!!,

            row.getString(11),
            row.getString(12),
            row.getString(13),
            row.getString(14),
        )
    }

    suspend fun findRegistration(
        sessionId: String,
        ctx: DBContext = db,
    ): InternalRegistration? {
        return ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("session_id", sessionId) },
                """
                    select
                        session_id,
                        first_names,
                        last_name,
                        email,
                        email_verified,
                        organization,
                        provider.timestamp_to_unix(created_at)::bigint,
                        provider.timestamp_to_unix(modified_at)::bigint,
                        email_verification_token,
                        identity_provider,
                        idp_identity,
                        organization_full_name,
                        department,
                        research_field,
                        position
                        
                    from
                        auth.registration
                    
                    where
                        session_id = :session_id
                """
            ).rows.singleOrNull()?.let { mapRow(it) }
        }
    }

    private val secureRandom = SecureRandom()
    private fun generateToken(): String {
        val array = ByteArray(64)
        secureRandom.nextBytes(array)
        return Base64.getUrlEncoder().encodeToString(array)
    }

    suspend fun submitRegistration(
        firstNames: String? = null,
        lastName: String? = null,
        email: String? = null,
        emailVerified: Boolean = false,
        organization: String? = null,
        idp: Int,
        idpIdentity: String,
        call: ApplicationCall? = null,
        ctx: DBContext = db,
    ): Registration {
        val sessionId = generateToken()
        ctx.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    setParameter("session_id", sessionId)
                    setParameter("first_names", firstNames)
                    setParameter("last_name", lastName)
                    setParameter("email", email)
                    setParameter("email_verified", emailVerified)
                    setParameter("organization", organization)
                    setParameter("idp", idp)
                    setParameter("idp_identity", idpIdentity)
                },
                """
                    insert into auth.registration
                        (session_id, first_names, last_name, email, email_verified, organization, identity_provider, idp_identity)
                    values
                        (:session_id, :first_names, :last_name, :email, :email_verified, :organization, :idp, :idp_identity)
                """
            )
        }

        call?.sendMessage(
            sessionId,
            "We need some additional information about you before we can finish your registration.",
            isError = false
        )

        return Registration(
            sessionId,
            firstNames,
            lastName,
            email,
        )
    }

    private suspend fun updateRegistration(
        registration: Registration,
        ctx: DBContext = db,
    ): Pair<InternalRegistration, Boolean>? {
        return ctx.withSession(remapExceptions = true) { session ->
            session.sendPreparedStatement(
                {
                    with(registration) {
                        setParameter("session_id", sessionId)
                        setParameter("first_names", firstNames.takeIf { !it.isNullOrBlank() })
                        setParameter("last_name", lastName.takeIf { !it.isNullOrBlank() })
                        setParameter("email", email.takeIf { !it.isNullOrBlank() })
                        setParameter("email_token", generateToken())

                        setParameter("organization_full_name", organizationFullName.takeIf { !it.isNullOrBlank() })
                        setParameter("department", department.takeIf { !it.isNullOrBlank() })
                        setParameter("research_field", researchField.takeIf { !it.isNullOrBlank() })
                        setParameter("position", position.takeIf { !it.isNullOrBlank() })
                    }
                },
                """
                    with email_tokens as (
                        select
                            email is distinct from :email::text as token_did_change,
                            case
                                when email = :email::text then r.email_verification_token
                                else :email_token
                            end as new_token
                        from
                            auth.registration r
                        where
                            session_id = :session_id
                    )
                    update auth.registration
                    set
                        session_id = :session_id,
                        first_names = :first_names,
                        last_name = :last_name,
                        email = :email,
                        modified_at = now(),
                        email_verification_token = tok.new_token,
                        
                        organization_full_name = :organization_full_name,
                        department = :department,
                        research_field = :research_field,
                        position = :position
                    from
                        email_tokens tok
                    where
                        session_id = :session_id
                    returning
                        session_id,
                        first_names,
                        last_name,
                        email,
                        email_verified,
                        organization,
                        provider.timestamp_to_unix(created_at)::bigint,
                        provider.timestamp_to_unix(modified_at)::bigint,
                        email_verification_token,
                        identity_provider,
                        idp_identity,
                        organization_full_name,
                        department,
                        research_field,
                        position,
                        tok.token_did_change as token_did_change
                """
            ).rows.singleOrNull()?.let { mapRow(it) to it.getBoolean("token_did_change")!! }
        }
    }

    private suspend fun deleteRegistration(
        sessionId: String,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("session_id", sessionId) },
                """
                    delete from auth.registration
                    where
                        session_id = :session_id
                """
            )
        }
    }

    suspend fun verifyEmail(
        token: String,
        callHandler: CallHandler<*, *, *>,
        ctx: DBContext = db,
    ) {
        val sessionId = ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("token", token) },
                """
                    update auth.registration
                    set 
                        email_verified = true,
                        modified_at = now()
                    where email_verification_token = :token
                    returning session_id
                """
            ).rows.singleOrNull()?.let { it.getString(0)!! }
        } ?: run {
            callHandler.sendMessage("", "Invalid link. Please make sure you have entered the link correctly.")
            return
        }

        callHandler.sendMessage(
            sessionId,
            "Your email has been verified.",
            isError = false,
        )
    }

    suspend fun requestNewVerificationEmail(
        sessionId: String,
        callHandler: CallHandler<*, *, *>,
        ctx: DBContext = db,
    ) {
        val (email, token) = ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("session_id", sessionId) },
                """
                    update auth.registration
                    set modified_at = now()
                    where
                        session_id = :session_id
                        and email is not null
                        and email_verification_token is not null
                        and not email_verified
                    returning
                        email, email_verification_token
                """
            ).rows.singleOrNull()?.let { row ->
                Pair(row.getString(0)!!, row.getString(1)!!)
            }
        } ?: run {
            callHandler.sendMessage(
                "",
                "Invalid link. Please try to log-in again.",
                isError = true
            )
            return
        }

        val success = sendVerificationEmail(email, token, sessionId, callHandler)
        if (!success) return

        callHandler.sendMessage(
            sessionId,
            "We have sent you a new email. Click the link to verify your account.",
            isError = false
        )
    }

    suspend fun completeRegistration(
        userRequest: Registration,
        callHandler: CallHandler<*, *, *>,
    ) {
        val (registration, emailDidChange) = updateRegistration(userRequest) ?: run {
            callHandler.sendMessage(
                userRequest.sessionId,
                "Registration process is no longer valid. Please start again from the beginning.",
            )
            return
        }

        if (emailLooksValid(userRequest.email) && emailDidChange) {
            val success = sendVerificationEmail(
                registration.email!!,
                registration.emailVerificationToken!!,
                registration.sessionId,
                callHandler
            )

            if (!success) return
        }

        completeRegistration(registration, callHandler)
    }

    private suspend fun completeRegistration(
        registration: InternalRegistration,
        callHandler: CallHandler<*, *, *>,
    ) {
        val ctx = callHandler.ctx as HttpCall

        val firstNames = registration.firstNames ?: run {
            callHandler.sendMessage(
                registration.sessionId,
                "Missing first name(s)",
            )
            return
        }

        val lastName = registration.lastName ?: run {
            callHandler.sendMessage(
                registration.sessionId,
                "Missing last name",
            )
            return
        }

        val organization = registration.organization

        val email = registration.email ?: run {
            callHandler.sendMessage(
                registration.sessionId,
                "Missing email",
            )
            return
        }

        if (!emailLooksValid(email)) {
            callHandler.sendMessage(registration.sessionId, "Invalid email address.")
            return
        }

        if (!registration.emailVerified) {
            callHandler.sendMessage(
                registration.sessionId,
                "To complete your registration, please click the link in the email we sent to you.",
                isError = false,
            )
            return
        }

        val username = usernameGenerator.generateUniqueName("$firstNames$lastName".replace(" ", ""))

        db.withSession { session ->
            val uid = users.insert(
                id = username,
                type = UserType.PERSON,
                role = Role.USER,
                firstNames = firstNames,
                lastName = lastName,
                email = email,
                organizationId = organization,
                connections = listOf(
                    IdentityProviderConnection(registration.idp, registration.idpIdentity, organization)
                ),
                ctx = session
            )

            session.sendPreparedStatement(
                {
                    setParameter("associated_user", uid)
                    setParameter("organization_full_name", registration.organizationFullName)
                    setParameter("department", registration.department)
                    setParameter("research_field", registration.researchField)
                    setParameter("position", registration.position)
                },
                """
                    insert into auth.additional_user_info (associated_user, organization_full_name, department, 
                        research_field, position)
                    values (:associated_user, :organization_full_name, :department, 
                        :research_field, :position)
                """
            )

            deleteRegistration(registration.sessionId, session)
            val person = users.findByUsername(username, session) as Person
            loginResponder.handleSuccessfulLogin(ctx.call, "web", person)
        }
    }

    private suspend fun CallHandler<*, *, *>.sendMessage(
        sessionId: String,
        message: String,
        isError: Boolean = true,
    ) {
        val ctx = ctx as HttpCall
        ctx.call.sendMessage(sessionId, message, isError)
        okContentAlreadyDelivered()
    }

    private suspend fun ApplicationCall.sendMessage(
        sessionId: String,
        message: String,
        isError: Boolean = true,
    ) {
        respondRedirect("/app/registration?sessionId=${urlEncode(sessionId)}&message=${urlEncode(message)}&errorHint=$isError")
    }

    private suspend fun sendVerificationEmail(
        email: String,
        emailToken: String,
        sessionId: String,
        callHandler: CallHandler<*, *, *>,
        ctx: DBContext = db,
    ): Boolean {
        // Rate limiting
        val remote = callHandler.ctx.remoteHost
        val count = ctx.withSession { session ->
            cleanup(session)

            session.sendPreparedStatement(
                {
                    setParameter("remote", remote)
                },
                """
                    select count(*)
                    from auth.verification_email_log
                    where ip_address = :remote
                """
            ).rows.singleOrNull()?.getLong(0) ?: 0L
        }

        if (count >= 10) {
            callHandler.sendMessage(
                sessionId,
                "You have requested too many emails from this address. Try again later.",
            )
            return false
        }

        ctx.withSession { session ->
            session.sendPreparedStatement(
                { setParameter("remote", remote) },
                """
                    insert into auth.verification_email_log(ip_address, created_at) 
                    values (:remote, now())
                """
            )
        }

        // Send the email if rate limiting didn't trigger
        Mails.sendDirect.call(
            bulkRequestOf(
                SendDirectMandatoryEmailRequest(
                    email,
                    Mail.VerifyEmailAddress("registration", emailToken)
                )
            ),
            serviceClient
        ).orThrow()
        return true
    }

    private suspend fun cleanup(
        ctx: DBContext = db,
    ) {
        val timestamp = nextCleanup.get()
        val now = Time.now()

        if (now >= timestamp && nextCleanup.compareAndSet(timestamp, now + 60 * 1000)) {
            ctx.withSession { session ->
                session.sendPreparedStatement(
                    {},
                    """
                        delete from auth.registration
                        where now() - created_at >= '120 minutes'::interval
                    """
                )

                session.sendPreparedStatement(
                    {},
                    """
                        delete from auth.verification_email_log
                        where now() - created_at >= '120 minutes'::interval
                    """
                )
            }
        }
    }

    private fun launchCleanupJob() {
        backgroundScope.launch {
            while (isActive) {
                Prometheus.countBackgroundTask(backgroundTaskName)
                val start = Time.now()
                try {
                    cleanup()
                } catch (ex: Throwable) {
                    log.warn(ex.toReadableStacktrace().toString())
                }
                val end = Time.now()
                Prometheus.measureBackgroundDuration(backgroundTaskName, end - start)

                delay(10_000)
            }
        }
    }

    companion object : Loggable {
        override val log = logger()
        private const val backgroundTaskName = "registration-cleanup"
    }
}

fun emailLooksValid(email: String?): Boolean {
    if (email == null) return false
    return email.contains("@") && email.substringAfter('@').contains('.')
}
