package dk.sdu.cloud.storage.ext.irods

import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.UserGroupTests
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsUserGroupTests : UserGroupTests() {
    override lateinit var allServices: StorageConnection

    @Before
    fun setup() {
        val factory = IRodsStorageConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
        ))
        allServices = factory.createForAccount("irodsadmin", "irodsadmin").orThrow()
    }
}