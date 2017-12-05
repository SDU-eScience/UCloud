package org.esciencecloud.storage.ext.irods

import org.esciencecloud.storage.ext.StorageConnection
import org.esciencecloud.storage.ext.StorageConnectionFactory
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
        adminStorageConnection = connFactoryStorage.createForAccount("rods", "rods").orThrow()
        userStorageConnection = connFactoryStorage.createForAccount("test", "test").orThrow()
    }
}