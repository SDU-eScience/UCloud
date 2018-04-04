package dk.sdu.cloud.storage.services.ext.irods

import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.connection.SettableJargonProperties
import org.irods.jargon.core.pub.IRODSFileSystem
import org.slf4j.LoggerFactory

class AccountServices(
    private val fs: IRODSFileSystem,
    val account: IRODSAccount,
    val connectionInformation: IRodsConnectionInformation
) {
    private val factory = fs.irodsAccessObjectFactory

    val environment by lazy { createIRodsProxy(factory.getEnvironmentalInfoAO(account)) }
    val zones by lazy { createIRodsProxy(factory.getZoneAO(account)) }
    val resources by lazy { createIRodsProxy(factory.getResourceAO(account)) }
    val ruleProcessings by lazy { createIRodsProxy(factory.getRuleProcessingAO(account)) }
    val remoteCommandExecution by lazy { createIRodsProxy(factory.getRemoteExecutionOfCommandsAO(account)) }
    val bulkFileOperations by lazy { createIRodsProxy(factory.getBulkFileOperationsAO(account)) }
    val quotas by lazy { createIRodsProxy(factory.getQuotaAO(account)) }
    val simplQueryExecutor by lazy { createIRodsProxy(factory.getSimpleQueryExecutorAO(account)) }
    val dataObjectAudits by lazy { createIRodsProxy(factory.getDataObjectAuditAO(account)) }
    val collectionAudits by lazy { createIRodsProxy(factory.getCollectionAuditAO(account)) }
    val mountedCollections by lazy { createIRodsProxy(factory.getMountedCollectionAO(account)) }
    val irodsregistrationoffilesao by lazy { createIRodsProxy(factory.getIRODSRegistrationOfFilesAO(account)) }
    val serverProperties by lazy { createIRodsProxy(factory.getIRODSServerProperties(account)) }
    val resourceGroups by lazy { createIRodsProxy(factory.getResourceGroupAO(account)) }
    val specificQueries by lazy { createIRodsProxy(factory.getSpecificQueryAO(account)) }
    val checksums by lazy { createIRodsProxy(factory.getDataObjectChecksumUtilitiesAO(account)) }

    val queryExecutor by lazy { createIRodsProxy(factory.getIRODSGenQueryExecutor(account)) }

    val users by lazy { createIRodsProxy(factory.getUserAO(account)) }
    val files by lazy { createIRodsProxy(factory.getIRODSFileFactory(account)) }
    val fileSystem by lazy { createIRodsProxy(factory.getIRODSFileSystemAO(account)) }
    val userGroups by lazy { createIRodsProxy(factory.getUserGroupAO(account)) }
    val dataObjects by lazy { createIRodsProxy(factory.getDataObjectAO(account)) }
    val collections by lazy { createIRodsProxy(factory.getCollectionAO(account)) }
    val collectionsAndObjectSearch by lazy {
        createIRodsProxy(factory.getCollectionAndDataObjectListAndSearchAO(account))
    }
    val dataTransfer by lazy { createIRodsProxy(factory.getDataTransferOperations(account)) }

    init {
        val properties = factory.irodsSession.jargonProperties
        if (properties is SettableJargonProperties) {
            properties.isComputeChecksumAfterTransfer = false
            properties.isComputeAndVerifyChecksumAfterTransfer = false
        } else {
            log.warn("Expected Jargon properties to be settable. Ignore this warning if manually overwritten")
        }
    }

    fun close() {
        fs.close(account)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccountServices::class.java)
    }
}

class IRodsConnectionInformation(
    val host: String, val port: Int, val zone: String, val storageResource: String, val authScheme: AuthScheme,
    val sslNegotiationPolicy: ClientServerNegotiationPolicy.SslNegotiationPolicy
)

