package dk.sdu.cloud.plugins.storage.ucloud

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orRethrowAs
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.InternalFile
import dk.sdu.cloud.plugins.RelativeInternalFile
import dk.sdu.cloud.plugins.UCloudFile
import dk.sdu.cloud.plugins.components
import dk.sdu.cloud.service.SimpleCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val driveProjectHomeName = "project-home"
const val driveShareName = "share"

class PathConverter(
    providerId: String,
    productCategory: String,
    private val rootDirectory: InternalFile,
    private val serviceClient: AuthenticatedClient,
) {
    val productReference = ProductReference(productCategory, productCategory, providerId)
    val projectHomeProductReference = ProductReference(driveProjectHomeName, productCategory, providerId)
    val shareProductReference = ProductReference(driveShareName, productCategory, providerId)

    val collectionCache = SimpleCache<String, FileCollection>(
        maxAge = 60_000 * 10L,
        lookup = { collectionId ->
            FileCollectionsControl.retrieve.call(
                ResourceRetrieveRequest(FileCollectionIncludeFlags(), collectionId),
                serviceClient
            ).orThrow()
        }
    )

    private val shareCache = SimpleCache<String, UCloudFile>(
        maxAge = 60_000,
        lookup = { collectionId ->
            UCloudFile.create(
                SharesControl.retrieve.call(
                    ResourceRetrieveRequest(ShareFlags(), collectionId.removePrefix(COLLECTION_SHARE_PREFIX)),
                    serviceClient
                ).orThrow().specification.sourceFilePath
            )
        }
    )

    private val cachedProviderIdsMutex = Mutex()
    private val cachedProviderIds = HashMap<String, FileCollection>()

    suspend fun fetchProject(file: UCloudFile): String? {
        val components = file.components()
        val collectionId = components[0]
        val owner = collectionCache.get(collectionId)?.owner
            ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        return owner.project
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun ucloudToInternal(file: UCloudFile): InternalFile {
        val components = file.components()
        val collectionId = components[0]
        val withoutCollection = components.drop(1)

        val collection = collectionCache.get(collectionId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val storedName = collection.providerGeneratedId ?: collection.id

        if (collection.providerGeneratedId != null) {
            cachedProviderIdsMutex.withLock {
                cachedProviderIds[storedName] = collection
            }
        }

        if (storedName.startsWith(COLLECTION_SHARE_PREFIX)) {
            val shareEntryPoint = (shareCache.get(storedName)
                ?: throw RPCException("Unknown file", HttpStatusCode.NotFound))

            return ucloudToInternal(
                UCloudFile.create(joinPath(*(shareEntryPoint.components() + withoutCollection).toTypedArray()))
            )
        } else if (storedName.startsWith(COLLECTION_HOME_PREFIX)) {
            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(HOME_DIRECTORY)
                    append('/')
                    append(storedName.removePrefix(COLLECTION_HOME_PREFIX))
                    for (component in withoutCollection) {
                        append('/')
                        append(component)
                    }
                }
            )
        } else if (storedName.startsWith(COLLECTION_PROJECT_PREFIX)) {
            val (projectId, repository) = storedName.removePrefix(COLLECTION_PROJECT_PREFIX).split("/")

            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(PROJECT_DIRECTORY)
                    append('/')
                    append(projectId)
                    append('/')
                    append(repository)
                    for (component in withoutCollection) {
                        append('/')
                        append(component)
                    }
                }
            )
        } else if (storedName.startsWith(COLLECTION_PROJECT_MEMBER_PREFIX)) {
            val (projectId, member) = storedName.removePrefix(COLLECTION_PROJECT_MEMBER_PREFIX).split("/")
            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(PROJECT_DIRECTORY)
                    append('/')
                    append(projectId)
                    append('/')
                    append(PERSONAL_REPOSITORY)
                    append('/')
                    append(member)
                    for (component in withoutCollection) {
                        append('/')
                        append(component)
                    }
                }
            )
        } else {
            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(COLLECTION_DIRECTORY)
                    append('/')
                    append(collectionId)
                    for (component in withoutCollection) {
                        append('/')
                        append(component)
                    }
                }
            )
        }
    }

    fun collectionLocation(collectionId: String): InternalFile {
        return InternalFile(
            buildString {
                append(rootDirectory.path)
                append('/')
                append(COLLECTION_DIRECTORY)
                append('/')
                append(collectionId)
            }
        )
    }

    private fun lookupCollectionFromInternalId(internalId: String): FileCollection {
        val cached = cachedProviderIds[internalId]
        if (cached != null) {
            return cached
        }

        return runBlocking {
            val collection = FileCollectionsControl.browse.call(
                ResourceBrowseRequest(
                    FileCollectionIncludeFlags(filterProviderIds = internalId)
                ),
                serviceClient
            ).orRethrowAs {
                throw RPCException("Could not fetch data about our collections", HttpStatusCode.BadGateway)
            }.items.singleOrNull()
                ?: throw RPCException("Collection does not exist: $internalId", HttpStatusCode.InternalServerError)

            cachedProviderIdsMutex.withLock {
                cachedProviderIds[internalId] = collection
            }

            collection
        }
    }

    fun internalToUCloud(file: InternalFile): UCloudFile {
        val components = file.path.removePrefix(rootDirectory.path).normalize().components()
        if (components.size <= 1) throw RPCException("Not a valid UCloud file", HttpStatusCode.InternalServerError, INVALID_FILE_ERROR_CODE)

        return UCloudFile.createFromPreNormalizedString(
            buildString {
                append('/')

                val startIdx: Int
                when (components[0]) {
                    HOME_DIRECTORY -> {
                        val collectionId = lookupCollectionFromInternalId("${COLLECTION_HOME_PREFIX}${components[1]}").id
                        append(collectionId)
                        startIdx = 2
                    }

                    PROJECT_DIRECTORY -> {
                        if (components.size < 3) throw RPCException("Not a valid UCloud file", HttpStatusCode.InternalServerError, INVALID_FILE_ERROR_CODE)
                        if (components.size > 3 && components[2] == PERSONAL_REPOSITORY) {
                            val collectionId = lookupCollectionFromInternalId(
                                COLLECTION_PROJECT_MEMBER_PREFIX +
                                    "${components[1]}/${components[3]}").id
                            append(collectionId)
                            startIdx = 4
                        } else {
                            if (components[2] == PERSONAL_REPOSITORY) {
                                throw RPCException("Not a valid UCloud file", HttpStatusCode.InternalServerError, INVALID_FILE_ERROR_CODE)
                            }

                            val collectionId = lookupCollectionFromInternalId(
                                "${COLLECTION_PROJECT_PREFIX}${components[1]}/${components[2]}"
                            ).id
                            append(collectionId)
                            startIdx = 3
                        }
                    }

                    COLLECTION_DIRECTORY -> {
                        append(components[1])
                        startIdx = 2
                    }

                    else -> throw RPCException("Not a valid UCloud file", HttpStatusCode.InternalServerError, INVALID_FILE_ERROR_CODE)
                }

                for ((idx, component) in components.withIndex()) {
                    if (idx < startIdx) continue
                    append('/')
                    append(component)
                }
            }
        )
    }

    fun internalToRelative(file: InternalFile): RelativeInternalFile {
        return RelativeInternalFile(file.path.removePrefix(rootDirectory.path))
    }

    fun relativeToInternal(file: RelativeInternalFile): InternalFile {
        return InternalFile(rootDirectory.path + file.path)
    }

    companion object {
        const val COLLECTION_HOME_PREFIX = "h-"
        const val COLLECTION_PROJECT_PREFIX = "p-"
        const val COLLECTION_PROJECT_MEMBER_PREFIX = "pm-"
        const val COLLECTION_SHARE_PREFIX = "s-"
        const val HOME_DIRECTORY = "home"
        const val PROJECT_DIRECTORY = "projects"
        const val COLLECTION_DIRECTORY = "collections"
        const val PERSONAL_REPOSITORY = "Members' Files"
        const val INVALID_FILE_ERROR_CODE = "INVALID_FILE"

    }
}

suspend fun PathConverter.ucloudToRelative(file: UCloudFile): RelativeInternalFile {
    return internalToRelative(ucloudToInternal(file))
}

fun PathConverter.relativeToUCloud(file: RelativeInternalFile): UCloudFile {
    return internalToUCloud(relativeToInternal(file))
}

fun isPersonalWorkspace(file: RelativeInternalFile): Boolean =
    file.path.startsWith("/${PathConverter.HOME_DIRECTORY}/")

fun isProjectWorkspace(file: RelativeInternalFile): Boolean =
    file.path.startsWith("/${PathConverter.PROJECT_DIRECTORY}/")

