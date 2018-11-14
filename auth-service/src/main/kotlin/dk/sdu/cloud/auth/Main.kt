package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonIgnore
import com.onelogin.saml2.settings.SettingsBuilder
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.RefreshingJWTCloudFeature
import dk.sdu.cloud.auth.api.installAuth
import dk.sdu.cloud.auth.services.saml.KtorUtils
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import dk.sdu.cloud.service.HibernateFeature
import dk.sdu.cloud.service.Micro
import dk.sdu.cloud.service.TokenValidationJWT
import dk.sdu.cloud.service.configuration
import dk.sdu.cloud.service.hibernateDatabase
import dk.sdu.cloud.service.initWithDefaultFeatures
import dk.sdu.cloud.service.install
import dk.sdu.cloud.service.kafka
import dk.sdu.cloud.service.runScriptHandler
import dk.sdu.cloud.service.serverProvider
import dk.sdu.cloud.service.tokenValidation
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.collections.set

private fun loadKeysAndInsertIntoProps(
    certsLocation: String,
    properties: Properties
): Pair<RSAPublicKey, RSAPrivateKey> {
    val certs =
        File(certsLocation).takeIf { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Missing 'certs' folder")
    val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile }
        ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
    val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile }
        ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

    val x509Text = x509Cert.readText()
    val privText = privateKey.readText()
    properties["onelogin.saml2.sp.x509cert"] = x509Text
    properties["onelogin.saml2.sp.privatekey"] = privText

    val loadedX509Cert = Util.loadCert(x509Text)
    val loadedPrivKey = Util.loadPrivateKey(privText)

    return Pair(
        loadedX509Cert.publicKey as RSAPublicKey,
        loadedPrivKey as RSAPrivateKey
    )
}

data class ServiceTokenExtension(
    val serviceName: String,
    val allowedScopes: List<String>
) {
    @get:JsonIgnore
    val parsedScopes = allowedScopes.map { SecurityScope.parseFromString(it) }
}

data class AuthConfiguration(
    val certsLocation: String = "./certs",
    val enablePasswords: Boolean = true,
    val enableWayf: Boolean = false,
    val production: Boolean = true,
    val tokenExtension: List<ServiceTokenExtension> = emptyList(),
    val trustedOrigins: List<String> = listOf("cloud.sdu.dk", "localhost")
)

fun main(args: Array<String>) {
    val micro = Micro().apply {
        initWithDefaultFeatures(AuthServiceDescription, args)
        install(HibernateFeature)
        installAuth()
    }

    if (micro.runScriptHandler()) return

    val configuration = micro.configuration.requestChunkAtOrNull("auth") ?: AuthConfiguration()
    KtorUtils.runningInProduction = configuration.production

    val samlProperties = Properties().apply {
        load(Server::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    val (_, priv) = loadKeysAndInsertIntoProps(configuration.certsLocation, samlProperties)
    val authSettings = SettingsBuilder().fromProperties(samlProperties).build().validateOrThrow()

    val tokenValidation = micro.tokenValidation as? TokenValidationJWT
        ?: throw IllegalStateException(
            "This service is for some reason not configured to use JWTs for validation. " +
                    "This is required!"
        )

    Server(
        db = micro.hibernateDatabase,
        jwtAlg = Algorithm.RSA256(null, priv),
        config = configuration,
        authSettings = authSettings,
        kafka = micro.kafka,
        ktor = micro.serverProvider,
        tokenValidation = tokenValidation,
        micro = micro
    ).start()
}
