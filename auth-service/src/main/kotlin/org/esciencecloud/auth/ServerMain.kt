package org.esciencecloud.auth

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

    val properties = Properties().apply {
        load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    val (pub, priv) = loadKeysAndInsertIntoProps(properties)

    AuthServer(properties, priv, TODO(), TODO()).createServer().start(wait = true)
}
