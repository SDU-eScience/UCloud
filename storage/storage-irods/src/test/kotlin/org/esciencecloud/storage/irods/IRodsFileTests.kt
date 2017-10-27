package org.esciencecloud.storage.irods

import org.esciencecloud.storage.Connection
import org.esciencecloud.storage.ConnectionFactory
import org.esciencecloud.storage.AbstractFileTests
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsFileTests : AbstractFileTests() {
    override lateinit var connFactory: ConnectionFactory
    override lateinit var adminConnection: Connection
    override lateinit var userConnection: Connection

    @Before
    fun setup() {
        connFactory = IRodsConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
        ))
        adminConnection = connFactory.createForAccount("rods", "rods")
        userConnection = connFactory.createForAccount("test", "test")
    }
}