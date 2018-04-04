package dk.sdu.cloud.storage.services.ext.irods

import dk.sdu.cloud.storage.api.User
import dk.sdu.cloud.storage.services.ext.*
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.exception.AuthenticationException
import org.irods.jargon.core.protovalues.UserTypeEnum
import org.irods.jargon.core.pub.IRODSFileSystem

class IRodsStorageConnection(private val services: AccountServices) : StorageConnection {
    override val connectedUser: User = with(services.account) { IRodsUser.fromUsernameAndZone(userName, zone) }
    val accountType: UserTypeEnum

    init {
        val name = services.users.findByName(services.account.userName)!!
        accountType = name.userType
    }

    override val paths: PathOperations = IRodsPathOperations(services)
    override val files: FileOperations = IRodsFileOperations(services)
    override val accessControl: AccessControlOperations = IRodsAccessControlOperations(services)
    override val fileQuery: FileQueryOperations = IRodsFileQueryOperations(connectedUser, paths, services)
    override val metadata: MetadataOperations = IRodsMetadataOperations(services, fileQuery)
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

    override fun createForAccount(username: String, password: String): StorageConnection {
        // Jargon uses a thread-local cache for connections. It is very important that we _do not_ cache these.
        // For this reason we will create a new thread (to circumvent this thread local cache) and then return the
        // result once the thread has connected.
        var result: StorageConnection? = null
        val thread = run {
            result = try {
                val account = with(connectionInformation) {
                    IRODSAccount.instance(
                        host,
                        port,
                        username,
                        password,
                        "/$zone/home/$username",
                        zone,
                        storageResource
                    )
                }

                account.authenticationScheme = connectionInformation.authScheme

                val csPolicy = ClientServerNegotiationPolicy()
                csPolicy.sslNegotiationPolicy = connectionInformation.sslNegotiationPolicy
                account.clientServerNegotiationPolicy = csPolicy

                val fs = IRODSFileSystem.instance()
                fs.irodsSession.x509TrustManager = SDUCloudTrustManager.trustManager
                IRodsStorageConnection(AccountServices(fs, account, connectionInformation))
            } catch (ex: AuthenticationException) {
                throw dk.sdu.cloud.storage.services.ext.AuthenticationException(ex.message ?: "")
            }
        }
        /*
        thread.start()
        thread.join()
        */
        return result ?: throw IllegalStateException("Result from connection was null. This was unexpected.")
    }
}