package dk.sdu.cloud.auth.services.saml

import com.onelogin.saml2.authn.AuthnRequest
import com.onelogin.saml2.authn.SamlResponse
import com.onelogin.saml2.exception.SettingsException
import com.onelogin.saml2.exception.XMLEntityException
import com.onelogin.saml2.logout.LogoutRequest
import com.onelogin.saml2.logout.LogoutResponse
import com.onelogin.saml2.settings.Saml2Settings
import com.onelogin.saml2.util.Constants
import com.onelogin.saml2.util.Util
import io.ktor.application.ApplicationCall
import io.ktor.http.Parameters
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.joda.time.Instant
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SignatureException
import java.util.*

private val LOGGER = LoggerFactory.getLogger("")

fun Saml2Settings.validateOrThrow(): Saml2Settings {
    val settingsErrors = checkSettings()
    if (!settingsErrors.isEmpty()) {
        var errorMsg = "Invalid settings: "
        errorMsg += StringUtils.join(settingsErrors, ", ")
        throw SettingsException(errorMsg, SettingsException.SETTINGS_INVALID)
    }
    return this
}

// This is a ktor port of the provided Auth class by java-saml-toolkit
// For the most part this just copy & pastes the solution and replaces servlet parts with ktor equivalents
// TODO The paramsMap is a hack. We can only call receive once, this is why we need it.
class SamlRequestProcessor(private val settings: Saml2Settings, private val call: ApplicationCall,
                           private val paramsMap: Parameters? = null) {
    /**
     * NameID.
     */
    var nameid: String? = null

    /**
     * NameIDFormat.
     */
    var nameidFormat: String? = null

    /**
     * SessionIndex. When the user is logged, this stored it from the AuthnStatement of the SAML Response
     */
    var sessionIndex: String? = null

    /**
     * SessionNotOnOrAfter. When the user is logged, this stored it from the AuthnStatement of the SAML Response
     */
    var sessionExpiration: DateTime? = null

    /**
     * The ID of the last message processed
     */
    var lastMessageId: String? = null

    /**
     * The ID of the last assertion processed
     */
    var lastAssertionId: String? = null

    /**
     * The NotOnOrAfter values of the last assertion processed
     */
    var lastAssertionNotOnOrAfter: List<Instant>? = null

    /**
     * User attributes data.
     */
    var attributes = HashMap<String, List<String>>()

    /**
     * If user is authenticated.
     */
    var authenticated = false

    /**
     * Stores any error.
     */
    var errors = ArrayList<String>()

    /**
     * Reason of the last error.
     */
    var errorReason: String? = null

    /**
     * The id of the last call (Authn or Logout) generated
     */
    var lastRequestId: String? = null

    /**
     * The most recently-constructed/processed XML SAML call
     * (AuthNRequest, LogoutRequest)
     */
    var lastRequest: String? = null

    /**
     * The most recently-constructed/processed XML SAML response
     * (SAMLResponse, LogoutResponse). If the SAMLResponse was
     * encrypted, by default tries to return the decrypted XML
     */
    var lastResponse: String? = null

    // --  Computed from settings --
    val ssoUrl: String get() = settings.idpSingleSignOnServiceUrl.toString()
    val sloUrl: String get() = settings.idpSingleLogoutServiceUrl.toString()
    val sloResponseUrl: String get() = settings.idpSingleLogoutServiceResponseUrl.toString()
    // -- /Computed from settings --


    @Throws(IOException::class, SettingsException::class)
    suspend fun login(
            returnTo: String? = null,
            forceAuthn: Boolean = false,
            isPassive: Boolean = false,
            setNameIdPolicy: Boolean = false,
            stay: Boolean = false
    ): String {
        val parameters = HashMap<String, String>()

        val authnRequest = AuthnRequest(
                settings,
                forceAuthn,
                isPassive,
                setNameIdPolicy
        )

        val samlRequest = authnRequest.encodedAuthnRequest

        parameters.put("SAMLRequest", samlRequest)

        val relayState: String = returnTo ?: KtorUtils.getSelfRoutedURLNoQuery(call)

        if (!relayState.isEmpty()) {
            parameters.put("RelayState", relayState)
        }

        if (settings.authnRequestsSigned) {
            val sigAlg = settings.signatureAlgorithm
            val signature = this.buildRequestSignature(samlRequest, relayState, sigAlg)

            parameters.put("SigAlg", sigAlg)
            parameters.put("Signature", signature)
        }

        val ssoUrl = ssoUrl
        lastRequestId = authnRequest.id
        lastRequest = authnRequest.authnRequestXml

        if (!stay) {
            LOGGER.debug("AuthNRequest sent to $ssoUrl --> $samlRequest")
        }
        return KtorUtils.sendRedirect(call, ssoUrl, parameters, stay)
    }

    @Throws(IOException::class, XMLEntityException::class, SettingsException::class)
    suspend fun logout(
            returnTo: String? = null,
            nameId: String? = null,
            sessionIndex: String? = null,
            stay: Boolean = false,
            nameidFormat: String? = null
    ): String {
        val parameters = HashMap<String, String>()

        val logoutRequest = LogoutRequest(settings, null, nameId, sessionIndex, nameidFormat)
        val samlLogoutRequest = logoutRequest.encodedLogoutRequest
        parameters.put("SAMLRequest", samlLogoutRequest)

        val relayState: String = returnTo ?: KtorUtils.getSelfRoutedURLNoQuery(call)

        if (!relayState.isEmpty()) {
            parameters.put("RelayState", relayState)
        }

        if (settings.logoutRequestSigned) {
            val sigAlg = settings.signatureAlgorithm
            val signature = this.buildRequestSignature(samlLogoutRequest, relayState, sigAlg)

            parameters.put("SigAlg", sigAlg)
            parameters.put("Signature", signature)
        }

        val sloUrl = sloUrl
        lastRequestId = logoutRequest.getId()
        lastRequest = logoutRequest.logoutRequestXml

        if (!stay) {
            LOGGER.debug("Logout call sent to $sloUrl --> $samlLogoutRequest")
        }
        return KtorUtils.sendRedirect(call, sloUrl, parameters, stay)
    }

    @Throws(Exception::class)
    suspend fun processResponse(requestId: String? = null) {
        authenticated = false
        val httpRequest = KtorUtils.makeHttpRequest(this.call, paramsMap)
        val samlResponseParameter = httpRequest.getParameter("SAMLResponse")

        if (samlResponseParameter != null) {
            val samlResponse = SamlResponse(settings, httpRequest)
            lastResponse = samlResponse.samlResponseXml

            if (samlResponse.isValid(requestId)) {
                nameid = samlResponse.nameId
                nameidFormat = samlResponse.nameIdFormat
                authenticated = true
                attributes = samlResponse.attributes
                sessionIndex = samlResponse.sessionIndex
                sessionExpiration = samlResponse.sessionNotOnOrAfter
                lastMessageId = samlResponse.id
                lastAssertionId = samlResponse.assertionId
                lastAssertionNotOnOrAfter = samlResponse.assertionNotOnOrAfter
                LOGGER.debug("processResponse success --> " + samlResponseParameter)
            } else {
                errors.add("invalid_response")
                LOGGER.error("processResponse error. invalid_response")
                LOGGER.debug(" --> " + samlResponseParameter)
                errorReason = samlResponse.error
            }
        } else {
            errors.add("invalid_binding")
            val errorMsg = "SAML Response not found, Only supported HTTP_POST Binding"
            LOGGER.error("processResponse error." + errorMsg)
            throw SAML2Error(errorMsg, SAML2Error.SAML_RESPONSE_NOT_FOUND)
        }
    }

    @Throws(Exception::class)
    suspend fun processSLO(
            keepLocalSession: Boolean = false,
            requestId: String? = null
    ) {
        val httpRequest = KtorUtils.makeHttpRequest(this.call, paramsMap)

        val samlRequestParameter = httpRequest.getParameter("SAMLRequest")
        val samlResponseParameter = httpRequest.getParameter("SAMLResponse")

        if (samlResponseParameter != null) {
            val logoutResponse = LogoutResponse(settings, httpRequest)
            lastResponse = logoutResponse.logoutResponseXml
            if (!logoutResponse.isValid(requestId)) {
                errors.add("invalid_logout_response")
                LOGGER.error("processSLO error. invalid_logout_response")
                LOGGER.debug(" --> " + samlResponseParameter)
                errorReason = logoutResponse.error
            } else {
                val status = logoutResponse.status
                if (status == null || status != Constants.STATUS_SUCCESS) {
                    errors.add("logout_not_success")
                    LOGGER.error("processSLO error. logout_not_success")
                    LOGGER.debug(" --> " + samlResponseParameter)
                } else {
                    lastMessageId = logoutResponse.id
                    LOGGER.debug("processSLO success --> " + samlResponseParameter)
                    if (!keepLocalSession) {
                        TODO("Invalidate state: call.getSession().invalidate()")
                    }
                }
            }
        } else if (samlRequestParameter != null) {
            val logoutRequest = LogoutRequest(settings, httpRequest)
            lastRequest = logoutRequest.logoutRequestXml
            if (!logoutRequest.isValid) {
                errors.add("invalid_logout_request")
                LOGGER.error("processSLO error. invalid_logout_request")
                LOGGER.debug(" --> " + samlRequestParameter)
                errorReason = logoutRequest.error
            } else {
                lastMessageId = logoutRequest.getId()
                LOGGER.debug("processSLO success --> " + samlRequestParameter)
                if (!keepLocalSession) {
                    TODO("Invalidate state: call.getSession().invalidate()")
                }

                val inResponseTo = logoutRequest.id
                val logoutResponseBuilder = LogoutResponse(settings, httpRequest)
                logoutResponseBuilder.build(inResponseTo)
                lastResponse = logoutResponseBuilder.logoutResponseXml

                val samlLogoutResponse = logoutResponseBuilder.encodedLogoutResponse

                val parameters = LinkedHashMap<String, String>()

                parameters.put("SAMLResponse", samlLogoutResponse)

                val relayState = call.parameters["RelayState"]
                if (relayState != null) {
                    parameters.put("RelayState", relayState)
                }

                if (settings.logoutResponseSigned) {
                    val sigAlg = settings.signatureAlgorithm
                    val signature = this.buildResponseSignature(samlLogoutResponse, relayState, sigAlg)

                    parameters.put("SigAlg", sigAlg)
                    parameters.put("Signature", signature)
                }

                val sloUrl = sloResponseUrl
                LOGGER.debug("Logout response sent to $sloUrl --> $samlLogoutResponse")
                KtorUtils.sendRedirect(call, sloUrl, parameters)
            }
        } else {
            errors.add("invalid_binding")
            val errorMsg = "SAML LogoutRequest/LogoutResponse not found. Only supported HTTP_REDIRECT Binding"
            LOGGER.error("processSLO error." + errorMsg)
            throw SAML2Error(errorMsg, SAML2Error.SAML_LOGOUTMESSAGE_NOT_FOUND)
        }
    }

    @Throws(SettingsException::class)
    private fun buildRequestSignature(samlRequest: String, relayState: String?, signAlgorithm: String): String {
        return buildSignature(samlRequest, relayState, signAlgorithm, "SAMLRequest")
    }

    @Throws(SettingsException::class)
    private fun buildResponseSignature(samlResponse: String, relayState: String?, signAlgorithm: String): String {
        return buildSignature(samlResponse, relayState, signAlgorithm, "SAMLResponse")
    }

    @Throws(SettingsException::class, IllegalArgumentException::class)
    private fun buildSignature(
            samlMessage: String,
            relayState: String?,
            signAlgorithm: String,
            type: String
    ): String {
        var actualSignAlgorithm = signAlgorithm
        if (StringUtils.isEmpty(actualSignAlgorithm)) {
            actualSignAlgorithm = Constants.RSA_SHA1
        }

        var signature = ""

        if (!settings.checkSPCerts()) {
            val errorMsg = "Trying to sign the $type but can't load the SP private key"
            LOGGER.error("buildSignature error. " + errorMsg)
            throw SettingsException(errorMsg, SettingsException.PRIVATE_KEY_NOT_FOUND)
        }

        val key = settings.sPkey

        var msg = type + "=" + Util.urlEncoder(samlMessage)
        if (relayState != null && StringUtils.isNotEmpty(relayState)) {
            msg += "&RelayState=" + Util.urlEncoder(relayState)
        }

        msg += "&SigAlg=" + Util.urlEncoder(actualSignAlgorithm)

        try {
            signature = Util.base64encoder(Util.sign(msg, key, actualSignAlgorithm))
        } catch (e: InvalidKeyException) {
            val errorMsg = "buildSignature error." + e.message
            LOGGER.error(errorMsg)
        } catch (e: NoSuchAlgorithmException) {
            val errorMsg = "buildSignature error." + e.message
            LOGGER.error(errorMsg)
        } catch (e: SignatureException) {
            val errorMsg = "buildSignature error." + e.message
            LOGGER.error(errorMsg)
        }

        if (signature.isEmpty()) {
            val errorMsg = "There was a problem when calculating the Signature of the " + type
            LOGGER.error("buildSignature error. " + errorMsg)
            throw IllegalArgumentException(errorMsg)
        }

        LOGGER.debug("buildResponseSignature success. --> " + signature)
        return signature
    }
}

typealias SAML2Error = com.onelogin.saml2.exception.Error