package dk.sdu.cloud.auth.services

import dk.sdu.cloud.auth.api.Principal
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.RPCException

class TwoFactorChallengeService {
    companion object : Loggable {
        override val log = logger()
    }
}

/**
 * A DAO for storing [TwoFactorCredentials] and [TwoFactorChallenge]
 */
interface TwoFactorDao<Session> {
    /**
     * Finds the enforced [TwoFactorCredentials] for a given user.
     *
     * If no such credentials exists `null` is returned
     */
    fun findEnforcedCredentialsOrNull(session: Session, principal: Principal): TwoFactorCredentials?

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
 * Given how powerful the [challengeId] they should have a value that expires relatively soon after creation.
 *
 * This implementation only supports TOTP.
 *
 * @property challengeId An unguessable ID
 * @property expiresAt Unix ms timestamp
 * @property twoFactorCredentials A reference to the credentials this challenge requires
 *
 * @see [TOTPService]
 */
sealed class TwoFactorChallenge {
    abstract val challengeId: String
    abstract val expiresAt: Long
    abstract val twoFactorCredentials: TwoFactorCredentials

    /**
     * A challenge presented during login.
     *
     * This requires the underlying challenge to be [TwoFactorCredentials.enforced]
     */
    data class Login(
        override val challengeId: String,
        override val expiresAt: Long,
        override val twoFactorCredentials: TwoFactorCredentials
    ) : TwoFactorChallenge() {
        init {
            if (!twoFactorCredentials.enforced) throw IllegalArgumentException("Bad challenge")
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
        override val twoFactorCredentials: TwoFactorCredentials
    ) : TwoFactorChallenge() {
        init {
            if (twoFactorCredentials.enforced) throw IllegalArgumentException("Bad challenge")
        }
    }
}

/**
 * Contains the credentials for two-factor authentication.
 *
 * This implementation only supports TOTP.
 *
 * @param principal The user these 2FA credentials apply to
 * *
 * @param sharedSecret The shared secret between user and server. See [TOTPCredentials.secretBase32Encoded]
 *
 * @param enforced When true these credentials must always be enforced when logging in.
 * A single [principal] is only allowed to have one pair of credentials that are [enforced]. A [principal] may have
 * multiple non-enforced credentials. A non-enforced pair of credentials are, for example, used during the setup phase.
 *
 * @param id A unique identifier for this set of credentials. It is created when inserting into a [TwoFactorDao].
 *
 * @see [TOTPService]
 */
data class TwoFactorCredentials(
    val principal: Principal,
    val sharedSecret: String,
    val enforced: Boolean,
    val id: Long? = null
)
