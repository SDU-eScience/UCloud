package org.esciencecloud.storage.ext.irods

import org.esciencecloud.storage.Error
import org.esciencecloud.storage.Ok
import org.esciencecloud.storage.Result
import org.esciencecloud.storage.ext.*
import org.esciencecloud.storage.model.User
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.exception.AuthenticationException
import org.irods.jargon.core.protovalues.UserTypeEnum
import org.irods.jargon.core.pub.IRODSFileSystem

class IRodsStorageConnection(private val services: AccountServices) : StorageConnection {
    override val connectedUser: User = with(services.account) { IRodsUser.fromUsernameAndZone(userName, zone) }
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

class IRodsStorageConnectionFactory(private val connectionInformation: IRodsConnectionInformation) :
        StorageConnectionFactory {
    private val objectFactory by lazy { IRODSFileSystem.instance().irodsAccessObjectFactory }

    override fun createForAccount(username: String, password: String): Result<StorageConnection> {
        // Jargon uses a thread-local cache for connections. It is very important that we _do not_ cache these.
        // For this reason we will create a new thread (to circumvent this thread local cache) and then return the
        // result once the thread has connected.
        var result: Result<StorageConnection>? = null
        val thread = Thread {
            result = try {
                val account = with(connectionInformation) {
                    IRODSAccount.instance(host, port, username, password, "/$zone/home/$username", zone, storageResource)
                }

                account.authenticationScheme = connectionInformation.authScheme

                val csPolicy = ClientServerNegotiationPolicy()
                csPolicy.sslNegotiationPolicy = connectionInformation.sslNegotiationPolicy
                account.clientServerNegotiationPolicy = csPolicy

                Ok(IRodsStorageConnection(AccountServices(objectFactory, account, connectionInformation)))
            } catch (ex: AuthenticationException) {
                Error.invalidAuthentication()
            }
        }
        thread.start()
        thread.join()
        return result ?: throw IllegalStateException("Result from connection was null. This was unexpected.")
    }
}