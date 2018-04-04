package dk.sdu.cloud.storage.services.ext.irods

import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy

class IRodsConnectionInformation(
    val host: String, val port: Int, val zone: String, val storageResource: String, val authScheme: AuthScheme,
    val sslNegotiationPolicy: ClientServerNegotiationPolicy.SslNegotiationPolicy
)