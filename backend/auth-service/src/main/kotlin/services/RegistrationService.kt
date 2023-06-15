package dk.sdu.cloud.auth.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.Prometheus
import dk.sdu.cloud.Role
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
    val wayfId: String,
) {
    fun toApiModel(): Registration = Registration(sessionId, firstNames, lastName, email)
}

class RegistrationService(
    private val db: DBContext,
    private val loginResponder: LoginResponder,
    private val serviceClient: AuthenticatedClient,
    private val users: UserCreationService,
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
            row.getString(9)!!,
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
                        wayf_id
                        
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
        return Base64.getEncoder().encodeToString(array)
    }

    suspend fun submitRegistration(
        firstNames: String? = null,
        lastName: String? = null,
        email: String? = null,
        emailVerified: Boolean = false,
        organization: String? = null,
        wayfId: String,
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
                    setParameter("wayf_id", wayfId)
                },
                """
                    insert into auth.registration
                        (session_id, first_names, last_name, email, email_verified, organization, wayf_id)
                    values
                        (:session_id, :first_names, :last_name, :email, :email_verified, :organization, :wayf_id)
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
                        setParameter("first_names", firstNames)
                        setParameter("last_name", lastName)
                        setParameter("email", email)
                        setParameter("email_token", generateToken())
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
                        email_verification_token = tok.new_token
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
                        wayf_id,
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
                        and now() - modified_at >= '5 minutes'::interval
                    returning
                        email, email_verification_token
                """
            ).rows.singleOrNull()?.let { row ->
                Pair(row.getString(0)!!, row.getString(1)!!)
            }
        } ?: run {
            callHandler.sendMessage(
                sessionId,
                "You have recently requested a new email. Try again in 5 minutes."
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

    private fun emailLooksValid(email: String?): Boolean {
        if (email == null) return false
        return email.contains("@") && email.substringAfter('@').contains('.')
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

        val person = Person.ByWAYF(
            usernameGenerator.generateUniqueName("$firstNames$lastName".replace(" ", "")),
            role = Role.USER,
            firstNames = firstNames,
            lastName = lastName,
            wayfId = registration.wayfId,
            email = email,
            organizationId = organization ?: "",
            serviceLicenseAgreement = 0
        )

        users.createUser(person)
        deleteRegistration(registration.sessionId)
        loginResponder.handleSuccessfulLogin(ctx.call, "web", person)
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
        respondRedirect("/app/registration?sessionId=$sessionId&message=${urlEncode(message)}&errorHint=$isError")
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
                    Mail.VerifyEmailAddress(emailToken)
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
