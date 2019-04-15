package dk.sdu.cloud.auth

import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.annotation.JsonIgnore
import com.onelogin.saml2.settings.SettingsBuilder
import com.onelogin.saml2.util.Util
import dk.sdu.cloud.SecurityScope
import dk.sdu.cloud.auth.api.AuthServiceDescription
import dk.sdu.cloud.auth.api.installAuth
import dk.sdu.cloud.auth.services.Service
import dk.sdu.cloud.auth.services.ServiceDAO
import dk.sdu.cloud.auth.services.saml.KtorUtils
import dk.sdu.cloud.auth.services.saml.validateOrThrow
import dk.sdu.cloud.micro.*
import dk.sdu.cloud.service.TokenValidationJWT
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.collections.set

data class Certificates(
    val x509Text: String,
    val privText: String,
    val publicKey: RSAPublicKey,
    val privateKey: RSAPrivateKey
)

private fun loadKeys(certsLocation: String): Certificates {
    val certs =
        File(certsLocation).takeIf { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Missing ${File(certsLocation).absolutePath} folder")
    val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile }
        ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
    val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile }
        ?: throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

    val x509Text = x509Cert.readText()
    val privText = privateKey.readText()

    val loadedX509Cert = Util.loadCert(x509Text)
    val loadedPrivKey = Util.loadPrivateKey(privText)

    return Certificates(
        x509Text,
        privText,
        loadedX509Cert.publicKey as RSAPublicKey,
        loadedPrivKey as RSAPrivateKey
    )
}

private fun insertKeysIntoProps(
    certificates: Certificates,
    properties: Properties
) {
    properties["onelogin.saml2.sp.x509cert"] = certificates.x509Text
    properties["onelogin.saml2.sp.privatekey"] = certificates.privText
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
    val wayfCerts: String = "./certs",
    val enablePasswords: Boolean = true,
    val enableWayf: Boolean = false,
    val production: Boolean = true,
    val tokenExtension: List<ServiceTokenExtension> = emptyList(),
    val trustedOrigins: List<String> = listOf("localhost"),
    val services: List<Service> = emptyList()
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
    configuration.services.forEach { ServiceDAO.insert(it) }

    val samlProperties = Properties().apply {
        load(Server::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    insertKeysIntoProps(loadKeys(configuration.wayfCerts), samlProperties)
    val jwtCerts = loadKeys(configuration.certsLocation)
    val authSettings = SettingsBuilder().fromProperties(samlProperties).build().validateOrThrow()

    val tokenValidation = micro.tokenValidation as TokenValidationJWT

    val algorithm = if (tokenValidation.algorithm.javaClass.canonicalName == "com.auth0.jwt.algorithms.HMACAlgorithm") {
        tokenValidation.algorithm
    } else {
        Algorithm.RSA256(null, jwtCerts.privateKey)
    }

    Server(
        jwtAlg = algorithm,
        config = configuration,
        authSettings = authSettings,
        micro = micro
    ).start()
}
