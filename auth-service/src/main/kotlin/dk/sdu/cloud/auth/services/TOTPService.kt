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
 * [scratchCodes] are an optional feature that allows a user to recover their account. They are supposed to be written
 * down in some secure location by the user. The server is also supposed to store them.
 *
 * [numberOfDigits] determines how many digits a verification code is supposed to contain.
 * This is unused by the Google Authenticator App.
 *
 * [periodInSeconds] determines how long a "period" should be. A period is how long a code stays valid for. This is
 * unused by the Google Authenticator App.
 *
 * [algorithm] determines which HMAC algorithm to use. This is unused by the Google Authenticator App (it always uses
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
    return URI(StringBuilder().apply {
        append("otpauth://")
        append("totp/")

        if (issuer != null) {
            if (issuer.contains(":")) throw IllegalArgumentException("Issuer cannot contain ':'")
            append(URLEncoder.encode(issuer, Charsets.UTF_8.name()))
            append(':')
        }

        append(URLEncoder.encode(displayName, Charsets.UTF_8.name()))
        append("?secret=")
        append(secretBase32Encoded)

        append("&algorithm=")
        append(algorithm.uriName)

        append("&digits=")
        append(numberOfDigits)

        append("&period=")
        append(periodInSeconds)

        if (issuer != null) {
            append('&')
            append(URLEncoder.encode(issuer, Charsets.UTF_8.name()))
        }

    }.toString())
}

enum class TOTPAlgorithm(val uriName: String) {
    SHA1("SHA1"),
    SHA256("SHA256"),
    SHA512("SHA512")
}

interface TOTPService {
    fun createSharedSecret(): TOTPCredentials
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