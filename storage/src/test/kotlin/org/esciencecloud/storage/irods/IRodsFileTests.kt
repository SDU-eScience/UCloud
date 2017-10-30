package org.esciencecloud.storage.irods

import org.esciencecloud.storage.StorageConnection
import org.esciencecloud.storage.StorageConnectionFactory
import org.esciencecloud.storage.AbstractFileTests
import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy.CS_NEG_REFUSE
import org.junit.Before

class IRodsFileTests : AbstractFileTests() {
    override lateinit var connFactoryStorage: StorageConnectionFactory
    override lateinit var adminStorageConnection: StorageConnection
    override lateinit var userStorageConnection: StorageConnection

    @Before
    fun setup() {
        connFactoryStorage = IRodsStorageConnectionFactory(IRodsConnectionInformation(
                host = "localhost",
                port = 1247,
                zone = "tempZone",
                storageResource = "radosRandomResc",
                authScheme = AuthScheme.STANDARD,
                sslNegotiationPolicy = CS_NEG_REFUSE
        ))
        adminStorageConnection = connFactoryStorage.createForAccount("rods", "rods")
        userStorageConnection = connFactoryStorage.createForAccount("test", "test")
    }
}