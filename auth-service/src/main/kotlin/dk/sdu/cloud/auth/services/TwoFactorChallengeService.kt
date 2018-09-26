package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Create2FACredentialsResponse
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.withTransaction
import io.ktor.http.HttpStatusCode
import java.util.*

/**
 * Exceptions that may be thrown by a [TwoFactorChallengeService]
 */
sealed class TwoFactorException(why: String, httpStatusCode: HttpStatusCode) : RPCException(why, httpStatusCode) {
    class AlreadyBound :
        TwoFactorException("An authenticator has already been bound to your account.", HttpStatusCode.BadRequest)

    class InvalidPrincipalType :
        TwoFactorException("Cannot apply 2FA operations for this user type.", HttpStatusCode.Forbidden)

    class InternalError : TwoFactorException("Internal Server Error", HttpStatusCode.InternalServerError)

    class InvalidChallenge :
        TwoFactorException("The two factor challenge has expired. Please try again.", HttpStatusCode.BadRequest)

    class NoActiveTwoFactor :
        TwoFactorException("You don't have an active 2FA device on your account.", HttpStatusCode.BadRequest)
}

/**
 * A service for handling 2FA
 */
class TwoFactorChallengeService<DBSession>(
    private val db: DBSessionFactory<DBSession>,
    private val twoFactorDAO: TwoFactorDAO<DBSession>,
    private val userDAO: UserDAO<DBSession>,
    private val totpService: TOTPService,
    private val qrService: QRService
) {
    /**
     * Creates initial 2FA credentials and bootstraps a challenge for those credentials.
     *
     * The end-user must complete this challenge before the 2FA device is activated on their account.
     */
    fun createSetupCredentialsAndChallenge(username: String): Create2FACredentialsResponse {
        val newCredentials = totpService.createSharedSecret()
        return db.withTransaction { dbSession ->
            val user = userDAO.findByIdOrNull(dbSession, username) ?: run {
                log.warn("Could not lookup user in createSetupCredentialsAndChallenge: $username")
                throw TwoFactorException.InternalError()
            }

            val person = user as? Person ?: throw TwoFactorException.InvalidPrincipalType()

            val enforcedCredentials = twoFactorDAO.findEnforcedCredentialsOrNull(dbSession, username)
            if (enforcedCredentials != null) throw TwoFactorException.AlreadyBound()

            val otpAuthUri = newCredentials.toOTPAuthURI(person.displayName, ISSUER).toASCIIString()
            val qrData = qrService.encode(otpAuthUri, QR_WIDTH_PX, QR_HEIGHT_PX).toDataURI()
            val twoFactorCredentials = TwoFactorCredentials(user, newCredentials.secretBase32Encoded, false)
            val credentialsId = twoFactorDAO.createCredentials(dbSession, twoFactorCredentials)

            val challengeId = createChallengeId()
            twoFactorDAO.createChallenge(
                dbSession,
                TwoFactorChallenge.Setup(
                    challengeId,
                    createChallengeExpiryTimestamp(),
                    twoFactorCredentials.copy(id = credentialsId)
                )
            )

            Create2FACredentialsResponse(otpAuthUri, qrData, challengeId)
        }
    }

    /**
     * Verifies that a challenge has been completed successfully
     */
    fun verifyChallenge(challengeId: String, verificationCode: Int): Pair<Boolean, TwoFactorChallenge> {
        val challenge = db.withTransaction { dbSession ->
            twoFactorDAO.findActiveChallengeOrNull(dbSession, challengeId)
                    ?: throw TwoFactorException.InvalidChallenge()
        }

        return Pair(
            totpService.verify(challenge.credentials.sharedSecret, verificationCode),
            challenge
        )
    }

    fun upgradeCredentials(credentials: TwoFactorCredentials) {
        if (credentials.enforced) throw IllegalArgumentException("credentials are already enforced")
        db.withTransaction { dbSession ->
            twoFactorDAO.createCredentials(dbSession, credentials.copy(enforced = true, id = null))
        }
    }

    /**
     * Creates a login challenge for a [username] with an enforced 2FA device
     */
    fun createLoginChallengeOrNull(username: String, service: String): String? {
        return db.withTransaction { dbSession ->
            val credentials = twoFactorDAO.findEnforcedCredentialsOrNull(dbSession, username)
                    ?: return null

            val challengeId = createChallengeId()
            twoFactorDAO.createChallenge(
                dbSession,
                TwoFactorChallenge.Login(
                    challengeId,
                    createChallengeExpiryTimestamp(),
                    credentials,
                    service
                )
            )

            challengeId
        }
    }

    private fun createChallengeId(): String = UUID.randomUUID().toString()

    private fun createChallengeExpiryTimestamp(): Long = System.currentTimeMillis() + CHALLENGE_EXPIRES_IN_MS

    companion object : Loggable {
        override val log = logger()

        const val ISSUER = "SDU Cloud"
        private const val QR_WIDTH_PX = 300
        private const val QR_HEIGHT_PX = 200

        private const val CHALLENGE_EXPIRES_IN_MS = 1000 * 60 * 10
    }
}

/**
 * A DAO for storing [TwoFactorCredentials] and [TwoFactorChallenge]
 */
interface TwoFactorDAO<Session> {
    /**
     * Finds the enforced [TwoFactorCredentials] for a given user.
     *
     * If no such credentials exists `null` is returned
     */
    fun findEnforcedCredentialsOrNull(session: Session, username: String): TwoFactorCredentials?

    /**
     * Finds an active (meaning [System.currentTimeMillis] > [TwoFactorChallenge.expiresAt]) [TwoFactorChallenge].
     *
     * If no such challenge is found `null` is returned.
     */
    fun findActiveChallengeOrNull(session: Session, challengeId: String): TwoFactorChallenge?

    /**
     * Creates a set of [TwoFactorCredentials]
     *
     * Note: This function does not mutate [twoFactorCredentials]
     *
     * If a set of enforced credentials already exists for [TwoFactorCredentials.principal] already exists then this
     * method will throw a relevant [RPCException]
     *
     * @return The newly created [TwoFactorCredentials.id]
     */
    fun createCredentials(session: Session, twoFactorCredentials: TwoFactorCredentials): Long

    /**
     * Creates a [TwoFactorChallenge]
     *
     * This requires [TwoFactorCredentials.id] to be non-null.
     */
    fun createChallenge(session: Session, challenge: TwoFactorChallenge)
}

/**
 * A [TwoFactorChallenge] requires the user to complete a challenge which proves access to a 2FA device.
 *
 * These challenges may be presented at different times. The [TwoFactorChallenge.Login] is presented when a user
 * attempts to login to the server, but has 2FA enabled on his account.
 *
 * A reference to this challenge proves that the user has already successfully provided credentials and is only
 * missing the 2FA step. As a result, __owning a valid [TwoFactorChallenge.challengeId] is as powerful as a
 * valid username + password combination__. It is crucial that the [challengeId] is sufficiently unguessable.
 *
 * Given how powerful the [challengeId] they should expire relatively soon after creation.
 *
 * This implementation only supports TOTP.
 *
 * @property challengeId An unguessable ID
 * @property expiresAt Unix ms timestamp
 * @property credentials A reference to the credentials this challenge requires
 *
 * @see [TOTPService]
 */
sealed class TwoFactorChallenge {
    abstract val challengeId: String
    abstract val expiresAt: Long
    abstract val credentials: TwoFactorCredentials

    /**
     * A challenge presented during login.
     *
     * This requires the underlying challenge to be [TwoFactorCredentials.enforced]
     */
    data class Login(
        override val challengeId: String,
        override val expiresAt: Long,
        override val credentials: TwoFactorCredentials,
        val service: String
    ) : TwoFactorChallenge() {
        init {
            if (!credentials.enforced) throw IllegalArgumentException("Bad challenge")
        }
    }

    /**
     * A challenge presented during setup of 2FA.
     *
     * This requires the underlying challenge to not be [TwoFactorCredentials.enforced]
     */
    data class Setup(
        override val challengeId: String,
        override val expiresAt: Long,
        override val credentials: TwoFactorCredentials
    ) : TwoFactorChallenge() {
        init {
            if (credentials.enforced) throw IllegalArgumentException("Bad challenge")
        }
    }
}

/**
 * Contains the credentials for two-factor authentication.
 *
 * This implementation only supports TOTP.
 *
 * @param principal The user these 2FA credentials apply to
 *
 * @param sharedSecret The shared secret between user and server. See [TOTPCredentials.secretBase32Encoded]
 *
 * @param enforced When true these credentials must always be enforced when logging in.
 * A single [principal] is only allowed to have one pair of credentials that are [enforced]. A [principal] may have
 * multiple non-enforced credentials. A non-enforced pair of credentials are, for example, used during the setup phase.
 *
 * @param id A unique identifier for this set of credentials. It is created when inserting into a [TwoFactorDAO].
 *
 * @see [TOTPService]
 */
data class TwoFactorCredentials(
    val principal: Principal,
    val sharedSecret: String,
    val enforced: Boolean,
    val id: Long? = null
)
