package org.esciencecloud.storage.irods

import org.esciencecloud.storage.Connection
import org.esciencecloud.storage.ConnectionFactory
import org.esciencecloud.storage.UserAdminTests
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsUserAdminTests : UserAdminTests() {
    override lateinit var connectionFactory: ConnectionFactory
    override lateinit var adminConn: Connection

    @Before
    fun setup() {
        connectionFactory = IRodsConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
        ))
        adminConn = connectionFactory.createForAccount("irodsadmin", "irodsadmin")
    }
}