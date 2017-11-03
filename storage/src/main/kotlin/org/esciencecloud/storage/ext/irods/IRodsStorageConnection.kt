package org.esciencecloud.storage.ext.irods

import org.esciencecloud.storage.ext.*
import org.esciencecloud.storage.model.User
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.protovalues.UserTypeEnum
import org.irods.jargon.core.pub.IRODSFileSystem

class IRodsStorageConnection(private val services: AccountServices) : StorageConnection {
    override val connectedUser: User = with (services.account) { User("$userName#$zone", userName, zone) }
    val accountType: UserTypeEnum

    init {
        val name = services.users.findByName(services.account.userName).capture()!!
        accountType = name.userType
    }

    override val paths: PathOperations = IRodsPathOperations(services)
    override val files: FileOperations = IRodsFileOperations(paths, services)
    override val metadata: MetadataOperations = IRodsMetadataOperations(paths, services)
    override val accessControl: AccessControlOperations = IRodsAccessControlOperations(paths, services)
    override val fileQuery: FileQueryOperations = IRodsFileQueryOperations(paths, services)
    override val users: UserOperations = IRodsUserOperations(services)
    override val groups: GroupOperations = IRodsGroupOperations(services)

    override val userAdmin: UserAdminOperations? =
            if (accountType == UserTypeEnum.RODS_ADMIN) IRodsUserAdminOperations(services) else null

    override fun close() {
        services.close()
    }
}

class IRodsStorageConnectionFactory(private val connectionInformation: IRodsConnectionInformation) : StorageConnectionFactory {
    private val objectFactory by lazy { IRODSFileSystem.instance().irodsAccessObjectFactory }

    override fun createForAccount(username: String, password: String): StorageConnection {
        val account = IRODSAccount.instance(connectionInformation.host, connectionInformation.port,
                username, password, "/" + connectionInformation.zone + "/home/" + username,
                connectionInformation.zone, connectionInformation.storageResource)

        account.authenticationScheme = connectionInformation.authScheme

        val csPolicy = ClientServerNegotiationPolicy()
        csPolicy.sslNegotiationPolicy = connectionInformation.sslNegotiationPolicy
        account.clientServerNegotiationPolicy = csPolicy
        return IRodsStorageConnection(AccountServices(objectFactory, account, connectionInformation))
    }
}