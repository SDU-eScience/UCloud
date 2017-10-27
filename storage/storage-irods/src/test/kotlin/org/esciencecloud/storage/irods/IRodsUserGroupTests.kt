package org.esciencecloud.storage.irods

import org.esciencecloud.storage.Connection
import org.esciencecloud.storage.UserGroupTests
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsUserGroupTests : UserGroupTests() {
    override lateinit var allServices: Connection

    @Before
    fun setup() {
        val factory = IRodsConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
        ))
        allServices = factory.createForAccount("irodsadmin", "irodsadmin")
    }
}