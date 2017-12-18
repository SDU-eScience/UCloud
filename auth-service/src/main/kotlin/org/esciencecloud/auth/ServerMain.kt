package org.esciencecloud.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.onelogin.saml2.util.Util
import org.esciencecloud.auth.api.Role
import org.esciencecloud.auth.services.RefreshTokens
import org.esciencecloud.auth.services.UserDAO
import org.esciencecloud.auth.services.UserUtils
import org.esciencecloud.auth.services.Users
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.io.File
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

private fun loadKeysAndInsertIntoProps(properties: Properties): Pair<RSAPublicKey, RSAPrivateKey> {
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

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("ServerMain")

    val mapper = jacksonObjectMapper()
    val config = mapper.readValue<AuthConfiguration>(File("auth_config.json"))

    if (args.isEmpty()) {
        val samlProperties = Properties().apply {
            load(AuthServer::class.java.classLoader.getResourceAsStream("saml.properties"))
        }
        val (_, priv) = loadKeysAndInsertIntoProps(samlProperties)

        AuthServer(samlProperties, priv, config, "localhost").start()
    } else {
        when (args[0]) {
            "generate-db" -> {
                log.info("Generating database (intended for development only)")
                log.info("Connecting...")
                Database.connect(
                        url = config.database.url,
                        driver = config.database.driver,

                        user = config.database.username,
                        password = config.database.password
                )
                log.info("OK")

                log.info("Creating tables...")
                transaction {
                    create(Users, RefreshTokens)
                    UserDAO.insert(UserUtils.createUserWithPassword(
                            fullName = "Dan Sebastian Thrane",
                            email = "dthrane@imada.sdu.dk",
                            role = Role.ADMIN,
                            password = "test"
                    ))
                }
                log.info("OK")
            }
        }
    }
}
