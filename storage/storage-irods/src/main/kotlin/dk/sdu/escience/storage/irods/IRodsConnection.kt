package dk.sdu.escience.storage.irods

import dk.sdu.escience.storage.*
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.protovalues.UserTypeEnum
import org.irods.jargon.core.pub.IRODSFileSystem

class IRodsConnection(private val services: AccountServices) : Connection {
    override val connectedUser: User = User(services.account.userName) // TODO Improve user definition
    val accountType: UserTypeEnum

    init {
        val name = services.users.findByName(services.account.userName)!!
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

class IRodsConnectionFactory(private val connectionInformation: IRodsConnectionInformation) : ConnectionFactory {
    private val objectFactory by lazy { IRODSFileSystem.instance().irodsAccessObjectFactory }

    override fun createForAccount(username: String, password: String): Connection {
        val account = IRODSAccount.instance(connectionInformation.host, connectionInformation.port,
                username, password, "/" + connectionInformation.zone + "/home/" + username,
                connectionInformation.zone, connectionInformation.storageResource)

        account.authenticationScheme = connectionInformation.authScheme

        val csPolicy = ClientServerNegotiationPolicy()
        csPolicy.sslNegotiationPolicy = connectionInformation.sslNegotiationPolicy
        account.clientServerNegotiationPolicy = csPolicy
        return IRodsConnection(AccountServices(objectFactory, account, connectionInformation))
    }
}