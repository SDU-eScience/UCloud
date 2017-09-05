package dk.sdu.escience.irods;

import org.irods.jargon.core.connection.IRODSAccount;
import org.irods.jargon.core.connection.IRODSServerProperties;
import org.irods.jargon.core.exception.JargonException;
import org.irods.jargon.core.pub.*;
import org.irods.jargon.core.pub.io.IRODSFileFactory;

class AccountServices {
    private final IRODSAccount account;
    private final IRODSAccessObjectFactory factory;

    // Lazy service instances
    private UserAO users = null;
    private EnvironmentalInfoAO environment = null;
    private ZoneAO zones = null;
    private ResourceAO resources = null;
    private IRODSFileSystemAO fileSystem = null;
    private IRODSFileFactory files = null;
    private UserGroupAO userGroups = null;
    private CollectionAO collections = null;
    private DataObjectAO dataObjects = null;
    private CollectionAndDataObjectListAndSearchAO collectionsAndObjectSearch = null;
    private RuleProcessingAO ruleProcessings = null;
    private DataTransferOperations dataTransfer = null;
    private RemoteExecutionOfCommandsAO remoteCommandExecution = null;
    private BulkFileOperationsAO bulkFileOperations = null;
    private QuotaAO quotas = null;
    private SimpleQueryExecutorAO queryExecutor = null;
    private DataObjectAuditAO dataObjectAudits = null;
    private CollectionAuditAO collectionAudits = null;
    private MountedCollectionAO mountedCollections = null;
    private IRODSRegistrationOfFilesAO irodsregistrationoffilesao = null;
    private IRODSServerProperties serverProperties = null;
    private ResourceGroupAO resourceGroups = null;
    private SpecificQueryAO specificQueries = null;
    private DataObjectChecksumUtilitiesAO checksums = null;

    AccountServices(IRODSAccessObjectFactory factory, IRODSAccount account) {
        this.account = account;
        this.factory = factory;
    }

    public IRODSAccount getAccount() {
        return account;
    }

    public UserAO getUsers() {
        if (users == null) {
            try {
                users = factory.getUserAO(account);
            } catch (JargonException e) {
                // Will never happen, unless this object is in an illegal state
                throw new IllegalStateException(e);
            }
        }
        return users;
    }

    public EnvironmentalInfoAO getEnvironment() {
        if (environment == null) {
            try {
                environment = factory.getEnvironmentalInfoAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return environment;
    }

    public ZoneAO getZones() {
        if (zones == null) {
            try {
                zones = factory.getZoneAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return zones;
    }

    public ResourceAO getResources() {
        if (resources == null) {
            try {
                resources = factory.getResourceAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return resources;
    }

    public IRODSFileSystemAO getFileSystem() {
        if (fileSystem == null) {
            try {
                fileSystem = factory.getIRODSFileSystemAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return fileSystem;
    }

    public IRODSFileFactory getFiles() {
        if (files == null) {
            try {
                files = factory.getIRODSFileFactory(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return files;
    }

    public UserGroupAO getUserGroups() {
        if (userGroups == null) {
            try {
                userGroups = factory.getUserGroupAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return userGroups;
    }

    public CollectionAO getCollections() {
        if (collections == null) {
            try {
                collections = factory.getCollectionAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return collections;
    }

    public DataObjectAO getDataObjects() {
        if (dataObjects == null) {
            try {
                dataObjects = factory.getDataObjectAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return dataObjects;
    }

    public CollectionAndDataObjectListAndSearchAO getCollectionsAndObjectSearch() {
        if (collectionsAndObjectSearch == null) {
            try {
                collectionsAndObjectSearch = factory.getCollectionAndDataObjectListAndSearchAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return collectionsAndObjectSearch;
    }

    public RuleProcessingAO getRuleProcessings() {
        if (ruleProcessings == null) {
            try {
                ruleProcessings = factory.getRuleProcessingAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return ruleProcessings;
    }

    public DataTransferOperations getDataTransfer() {
        if (dataTransfer == null) {
            try {
                dataTransfer = factory.getDataTransferOperations(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return dataTransfer;
    }

    public RemoteExecutionOfCommandsAO getRemoteCommandExecution() {
        if (remoteCommandExecution == null) {
            try {
                remoteCommandExecution = factory.getRemoteExecutionOfCommandsAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return remoteCommandExecution;
    }

    public BulkFileOperationsAO getBulkFileOperations() {
        if (bulkFileOperations == null) {
            try {
                bulkFileOperations = factory.getBulkFileOperationsAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return bulkFileOperations;
    }

    public QuotaAO getQuotas() {
        if (quotas == null) {
            try {
                quotas = factory.getQuotaAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return quotas;
    }

    public SimpleQueryExecutorAO getQueryExecutor() {
        if (queryExecutor == null) {
            try {
                queryExecutor = factory.getSimpleQueryExecutorAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return queryExecutor;
    }

    public DataObjectAuditAO getDataObjectAudits() {
        if (dataObjectAudits == null) {
            try {
                dataObjectAudits = factory.getDataObjectAuditAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return dataObjectAudits;
    }

    public CollectionAuditAO getCollectionAudits() {
        if (collectionAudits == null) {
            try {
                collectionAudits = factory.getCollectionAuditAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return collectionAudits;
    }

    public MountedCollectionAO getMountedCollections() {
        if (mountedCollections == null) {
            try {
                mountedCollections = factory.getMountedCollectionAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return mountedCollections;
    }

    public IRODSRegistrationOfFilesAO getIrodsregistrationoffilesao() {
        if (irodsregistrationoffilesao == null) {
            try {
                irodsregistrationoffilesao = factory.getIRODSRegistrationOfFilesAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return irodsregistrationoffilesao;
    }

    public IRODSServerProperties getServerProperties() {
        if (serverProperties == null) {
            try {
                serverProperties = factory.getIRODSServerProperties(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return serverProperties;
    }

    public ResourceGroupAO getResourceGroups() {
        if (resourceGroups == null) {
            try {
                resourceGroups = factory.getResourceGroupAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return resourceGroups;
    }

    public SpecificQueryAO getSpecificQueries() {
        if (specificQueries == null) {
            try {
                specificQueries = factory.getSpecificQueryAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return specificQueries;
    }

    public DataObjectChecksumUtilitiesAO getChecksums() {
        if (checksums == null) {
            try {
                checksums = factory.getDataObjectChecksumUtilitiesAO(account);
            } catch (JargonException e) {
                throw new IllegalStateException(e);
            }
        }
        return checksums;
    }

    public void close() {
        factory.closeSessionAndEatExceptions();

    }
}
