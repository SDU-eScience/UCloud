package org.esciencecloud.auth

import com.onelogin.saml2.settings.SettingsBuilder
import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.route
import io.ktor.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.esciencecloud.auth.saml.Auth
import org.esciencecloud.auth.saml.validateOrThrow
import java.io.File
import java.util.*

object F

fun main(args: Array<String>) {
    val certs = File("certs").takeIf { it.exists() && it.isDirectory } ?:
            throw IllegalStateException("Missing 'certs' folder")
    val x509Cert = File(certs, "cert.pem").takeIf { it.exists() && it.isFile } ?:
            throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name cert.pem")
    val privateKey = File(certs, "key.pem").takeIf { it.exists() && it.isFile } ?:
            throw IllegalStateException("Missing x509 cert. Expected at: ${certs.absolutePath} with name key.pem")

    val properties = Properties().apply {
        load(F::class.java.classLoader.getResourceAsStream("saml.properties"))
    }
    properties["onelogin.saml2.sp.x509cert"] = x509Cert.readText()
    properties["onelogin.saml2.sp.privatekey"] = privateKey.readText()

    val settings = SettingsBuilder().fromProperties(properties).build().validateOrThrow()
    embeddedServer(CIO, port = 8080) {
        routing {
            route("saml") {
                get("metadata") {
                    call.respondText(settings.spMetadata, ContentType.Application.Xml)
                }

                get("login") {
                    val auth = Auth(settings, call)
                    auth.login(setNameIdPolicy = true)
                }
                post("acs") {
                    val auth = Auth(settings, call)
                    auth.processResponse()

                    println(auth.attributes)
                    println(auth.authenticated)
                    if (auth.authenticated) {
                        call.respondRedirect("/yay")
                    } else {
                        call.respondRedirect("/nay")
                    }
                }
            }

            get("yay") {
                call.respondText("Good!")
            }

            get("nay") {
                call.respondText("Bad!")
            }
        }
    }.start(wait = true)
}