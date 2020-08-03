package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Create2FACredentialsResponse
import dk.sdu.cloud.auth.api.Person
import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Time
import dk.sdu.cloud.service.db.DBSessionFactory
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.withSession
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
        TwoFactorException("The two factor challenge has expired. Please try again.", HttpStatusCode.NotFound)

}

/**
 * A service for handling 2FA
 */
class TwoFactorChallengeService(
    private val db: DBContext,
    private val twoFactorDAO: TwoFactorAsyncDAO,
    private val userDAO: UserAsyncDAO,
    private val totpService: TOTPService,
    private val qrService: QRService
) {
    /**
     * Creates initial 2FA credentials and bootstraps a challenge for those credentials.
     *
     * The end-user must complete this challenge before the 2FA device is activated on their account.
     */
    suspend fun createSetupCredentialsAndChallenge(username: String): Create2FACredentialsResponse {
        val newCredentials = totpService.createSharedSecret()
        return db.withSession { session ->
            val user = userDAO.findByIdOrNull(session, username) ?: run {
                log.warn("Could not lookup user in createSetupCredentialsAndChallenge: $username")
                throw TwoFactorException.InternalError()
            }

            val person = user as? Person ?: throw TwoFactorException.InvalidPrincipalType()

            val enforcedCredentials = twoFactorDAO.findEnforcedCredentialsOrNull(session, username)
            if (enforcedCredentials != null) throw TwoFactorException.AlreadyBound()

            val otpAuthUri = newCredentials.toOTPAuthURI(person.displayName, ISSUER).toASCIIString()
            val qrData = qrService.encode(otpAuthUri, QR_WIDTH_PX, QR_HEIGHT_PX).toDataURI()
            val twoFactorCredentials = TwoFactorCredentials(user, newCredentials.secretBase32Encoded, false)
            val credentialsId = twoFactorDAO.createCredentials(session, twoFactorCredentials)

            val challengeId = createChallengeId()
            twoFactorDAO.createChallenge(
                session,
                TwoFactorChallenge(
                    TwoFactorChallengeType.SETUP.name,
                    challengeId,
                    createChallengeExpiryTimestamp(),
                    twoFactorCredentials.copy(id = credentialsId)
                )
            )

            Create2FACredentialsResponse(otpAuthUri, qrData, newCredentials.secretBase32Encoded, challengeId)
        }
    }

    /**
     * Verifies that a challenge has been completed successfully
     */
    suspend fun verifyChallenge(challengeId: String, verificationCode: Int): Pair<Boolean, TwoFactorChallenge> {
        val challenge = twoFactorDAO.findActiveChallengeOrNull(db, challengeId)
            ?: throw TwoFactorException.InvalidChallenge()


        return Pair(
            totpService.verify(challenge.credentials.sharedSecret, verificationCode),
            challenge
        )
    }

    suspend fun upgradeCredentials(credentials: TwoFactorCredentials) {
        if (credentials.enforced) throw IllegalArgumentException("credentials are already enforced")
        twoFactorDAO.createCredentials(db, credentials.copy(enforced = true, id = null))
    }

    /**
     * Creates a login challenge for a [username] with an enforced 2FA device
     */
    suspend fun createLoginChallengeOrNull(username: String, service: String): String? {
        return db.withSession { session ->
            val credentials = twoFactorDAO.findEnforcedCredentialsOrNull(session, username)
                ?: return@withSession null

            val challengeId = createChallengeId()
            twoFactorDAO.createChallenge(
                session,
                TwoFactorChallenge(
                    TwoFactorChallengeType.LOGIN.name,
                    challengeId,
                    createChallengeExpiryTimestamp(),
                    credentials,
                    service
                )
            )

            challengeId
        }
    }

    suspend fun isConnected(username: String): Boolean {
        return twoFactorDAO.findEnforcedCredentialsOrNull(db, username) != null
    }

    private fun createChallengeId(): String = UUID.randomUUID().toString()

    private fun createChallengeExpiryTimestamp(): Long = Time.now() + CHALLENGE_EXPIRES_IN_MS

    companion object : Loggable {
        override val log = logger()

        const val ISSUER = "SDU Cloud"
        private const val QR_WIDTH_PX = 200
        private const val QR_HEIGHT_PX = 200

        private const val CHALLENGE_EXPIRES_IN_MS = 1000 * 60 * 10
    }
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
enum class TwoFactorChallengeType {
    LOGIN,
    SETUP;
}

data class TwoFactorChallenge(
    val type: String,
    val challengeId: String,
    val expiresAt: Long,
    val credentials: TwoFactorCredentials,
    val service: String? = null
) {
    init {
        /**
         * A challenge presented during setup of 2FA.
         *
         * This requires the underlying challenge to not be [TwoFactorCredentials.enforced]
         */
        if (type.contains(TwoFactorChallengeType.SETUP.name)) {
            if (credentials.enforced) throw IllegalArgumentException("Bad challenge")
        }
        /**
         * A challenge presented during login.
         *
         * This requires the underlying challenge to be [TwoFactorCredentials.enforced]
         */
        if (type.contains(TwoFactorChallengeType.LOGIN.name)) {
            if (!credentials.enforced) throw IllegalArgumentException("Bad challenge")

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
