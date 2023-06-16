package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkRequestOf
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.withSession
import java.security.SecureRandom
import java.util.*

sealed class UserException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyExists : UserException("User already exists", HttpStatusCode.Conflict)
    class NotFound : UserException("User not found", HttpStatusCode.NotFound)
    class InvalidAuthentication : UserException("Invalid username or password", HttpStatusCode.BadRequest)
}

class UserCreationService(
    private val db: DBContext,
    private val userDao: UserAsyncDAO,
    private val serviceClient: AuthenticatedClient,
) {
    suspend fun createUser(user: Principal) {
        createUsers(listOf(user))
    }

    suspend fun createUsers(users: List<Principal>) {
        db.withSession { session ->
            users.forEach { user ->
                val exists = userDao.findByIdOrNull(session, user.id) != null
                if (exists) {
                    throw UserException.AlreadyExists()
                } else {
                    log.info("Creating user: $user")
                    userDao.insert(session, user)
                }
            }
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
        email: String?
    ) {
        if (email != null && !emailLooksValid(email)) {
            throw RPCException("This email does not look valid. Please try again.", HttpStatusCode.BadRequest)
        }

        val token = generateToken()
        val recipientEmail = db.withSession { session ->
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

    suspend fun verifyUserInfoUpdate(token: String): Boolean {
        return db.withSession { session ->
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

    suspend fun getUserInfo(username: String): UserInformation {
        return userDao.getUserInfo(db, username)
    }

    companion object : Loggable {
        override val log = logger()
    }
}
