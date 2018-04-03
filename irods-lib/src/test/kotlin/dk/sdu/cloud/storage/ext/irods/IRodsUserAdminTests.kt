package dk.sdu.cloud.storage.ext.irods

import dk.sdu.cloud.storage.UserAdminTests
import dk.sdu.cloud.storage.ext.StorageConnection
import dk.sdu.cloud.storage.ext.StorageConnectionFactory
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsUserAdminTests : UserAdminTests() {
    override lateinit var storageConnectionFactory: StorageConnectionFactory
    override lateinit var adminConn: StorageConnection

    @Before
    fun setup() {
        storageConnectionFactory = IRodsStorageConnectionFactory(
            IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
            )
        )
        adminConn = storageConnectionFactory.createForAccount("irodsadmin", "irodsadmin").orThrow()
    }
}