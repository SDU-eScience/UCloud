package org.esciencecloud.storage.ext.irods

import org.irods.jargon.core.connection.AuthScheme
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy
import org.irods.jargon.core.connection.IRODSAccount
import org.irods.jargon.core.connection.SettableJargonProperties
import org.irods.jargon.core.pub.IRODSFileSystem

class AccountServices(private val fs: IRODSFileSystem, val account: IRODSAccount,
                      val connectionInformation: IRodsConnectionInformation) {
    private val factory = fs.irodsAccessObjectFactory

    val environment by lazy { factory.getEnvironmentalInfoAO(account) }
    val zones by lazy { factory.getZoneAO(account) }
    val resources by lazy { factory.getResourceAO(account) }
    val collections by lazy { factory.getCollectionAO(account) }
    val ruleProcessings by lazy { factory.getRuleProcessingAO(account) }
    val remoteCommandExecution by lazy { factory.getRemoteExecutionOfCommandsAO(account) }
    val bulkFileOperations by lazy { factory.getBulkFileOperationsAO(account) }
    val quotas by lazy { factory.getQuotaAO(account) }
    val simplQueryExecutor by lazy { factory.getSimpleQueryExecutorAO(account) }
    val dataObjectAudits by lazy { factory.getDataObjectAuditAO(account) }
    val collectionAudits by lazy { factory.getCollectionAuditAO(account) }
    val mountedCollections by lazy { factory.getMountedCollectionAO(account) }
    val irodsregistrationoffilesao by lazy { factory.getIRODSRegistrationOfFilesAO(account) }
    val serverProperties by lazy { factory.getIRODSServerProperties(account) }
    val resourceGroups by lazy { factory.getResourceGroupAO(account) }
    val specificQueries by lazy { factory.getSpecificQueryAO(account) }
    val checksums by lazy { factory.getDataObjectChecksumUtilitiesAO(account) }

    val queryExecutor by lazy { factory.getIRODSGenQueryExecutor(account) }

    // TODO Accessing these can give JargonExceptions if not wrapped. Correct exception should be a ConnectionException
    val users by lazy { UserAOWrapper(factory.getUserAO(account)) }
    val files by lazy { FileFactoryWrapper(factory.getIRODSFileFactory(account)) }
    val fileSystem by lazy { FileSystemWrapper(factory.getIRODSFileSystemAO(account)) }
    val userGroups by lazy { UserGroupsWrapper(factory.getUserGroupAO(account)) }
    val dataObjects by lazy { DataObjectsWrapper(factory.getDataObjectAO(account)) }
    val collectionsAndObjectSearch by lazy {
        CollectionsAndObjectSearchWrapper(factory.getCollectionAndDataObjectListAndSearchAO(account))
    }
    val dataTransfer by lazy { DataTransferWrapper(factory.getDataTransferOperations(account)) }

    init {
        val properties = factory.irodsSession.jargonProperties
        if (properties is SettableJargonProperties) {
            properties.isComputeChecksumAfterTransfer = false
            properties.isComputeAndVerifyChecksumAfterTransfer = false
        } else {
            System.err.println("Expected Jargon properties to be settable. Ignore this warning if manually overwritten")
        }
    }

    fun close() {
        fs.close(account)
    }

}

class IRodsConnectionInformation(
        val host: String, val port: Int, val zone: String, val storageResource: String, val authScheme: AuthScheme,
        val sslNegotiationPolicy: ClientServerNegotiationPolicy.SslNegotiationPolicy
)

