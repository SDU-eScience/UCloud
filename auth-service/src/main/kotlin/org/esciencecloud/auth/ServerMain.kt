package org.esciencecloud.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.onelogin.saml2.util.Util
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

fun main(args: Array<String>) {
    fun loadKeysAndInsertIntoProps(properties: Properties): Pair<RSAPublicKey, RSAPrivateKey> {
        val certs = File("certs").takeIf { it.exists() && it.isDirectory } ?:
                throw IllegalStateException("Missing 'certs' folder")
        val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile } ?:
                throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
        val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile } ?:
                throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

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

    val samlProperties = Properties().apply {
        load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    val (_, priv) = loadKeysAndInsertIntoProps(samlProperties)

    val mapper = jacksonObjectMapper()
    val config = mapper.readValue<AuthConfiguration>(File("auth_config.json"))

    AuthServer(samlProperties, priv, config, "localhost").start()
}
