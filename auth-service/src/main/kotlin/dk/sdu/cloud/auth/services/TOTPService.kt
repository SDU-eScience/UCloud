package dk.sdu.cloud.auth.services

import com.warrenstrange.googleauth.GoogleAuthenticator
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig
import com.warrenstrange.googleauth.HmacHashFunction
import java.net.URI
import java.net.URLEncoder

/**
 * Initial TOTP credentials for a user.
 *
 * The user should be informed about each of these, as they are needed to generate codes.
 * The server should also save them. However, a few of these parameters are optional and/or unused by some clients.
 *
 * @param secretBase32Encoded A shared secret between the user and server, pre-encoded as a base32 string
 *
 * @param scratchCodes are an optional feature that allows a user to recover their account. They are supposed to be
 * written down in some secure location by the user. The server is also supposed to store them.
 *
 * @param numberOfDigits determines how many digits a verification code is supposed to contain.
 * This is unused by the Google Authenticator App.
 *
 * @param periodInSeconds determines how long a "period" should be. A period is how long a code stays valid for. This is
 * unused by the Google Authenticator App.
 *
 * @param algorithm determines which HMAC algorithm to use. This is unused by the Google Authenticator App (it always uses
 * [TOTPAlgorithm.SHA1])
 */
data class TOTPCredentials(
    val secretBase32Encoded: String,
    val scratchCodes: List<Int>,
    val algorithm: TOTPAlgorithm = TOTPAlgorithm.SHA1,
    val numberOfDigits: Int = 6,
    val periodInSeconds: Int = 30
)

/**
 * Converts the initial TOTP credentials into an "otpauth" URI
 *
 * otpauth URIs are interpreted by authenticator apps (such as Google Authenticator) to create a new entry.
 * An otpauth URI is usually presented to the user in the form of a QR-code.
 *
 * See https://github.com/google/google-authenticator/wiki/Key-Uri-Format for more information about the URI format.
 */
fun TOTPCredentials.toOTPAuthURI(displayName: String, issuer: String? = null): URI {
    return URI(
        "otpauth",
        "totp",

        StringBuilder().apply {
            append('/')
            if (issuer != null) {
                if (issuer.contains(":")) throw IllegalArgumentException("Issuer cannot contain ':'")
                append(issuer)
                append(':')
            }

            append(displayName)
        }.toString(),

        StringBuilder().apply {
            append("secret=")
            append(secretBase32Encoded)

            append("&algorithm=")
            append(algorithm.uriName)

            append("&digits=")
            append(numberOfDigits)

            append("&period=")
            append(periodInSeconds)

            if (issuer != null) {
                append("&issuer=")
                append(issuer)
            }
        }.toString(),

        null
    )
}

/**
 * An HMAC algorithm used for TOTP
 */
enum class TOTPAlgorithm(val uriName: String) {
    SHA1("SHA1"),
    SHA256("SHA256"),
    SHA512("SHA512")
}

/**
 * A service for creating and verifying TOTP (time-based one time passwords).
 *
 * For the end-user TOTPs are usually generated from a TOTP device, such as the Google Authenticator App.
 */
interface TOTPService {
    /**
     * Creates the initial credentials for the user
     *
     * @see TOTPCredentials
     */
    fun createSharedSecret(): TOTPCredentials

    /**
     * Verifies the [verificationCode] given a [sharedSecret] between the user and server.
     */
    fun verify(sharedSecret: String, verificationCode: Int): Boolean
}

/**
 * Implements the [TOTPService] using the wstrange/google-authenticator library
 *
 * See github.com/wstrange/google-authenticator
 */
class WSTOTPService : TOTPService {
    private val config = GoogleAuthenticatorConfig()
    private val instance = GoogleAuthenticator(config)

    override fun createSharedSecret(): TOTPCredentials {
        val credentials = instance.createCredentials()
        return TOTPCredentials(
            credentials.key,
            credentials.scratchCodes,
            config.hmacHashFunction.toAlgorithm(),
            config.codeDigits,
            (config.timeStepSizeInMillis / 1000).toInt()
        )
    }

    override fun verify(sharedSecret: String, verificationCode: Int): Boolean {
        return instance.authorize(sharedSecret, verificationCode)
    }

    private fun HmacHashFunction.toAlgorithm(): TOTPAlgorithm = when (this) {
        HmacHashFunction.HmacSHA1 -> TOTPAlgorithm.SHA1
        HmacHashFunction.HmacSHA256 -> TOTPAlgorithm.SHA256
        HmacHashFunction.HmacSHA512 -> TOTPAlgorithm.SHA512
    }
}