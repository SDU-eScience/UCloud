package dk.sdu.cloud.file.ucloud.services

import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.UCLOUD_PROVIDER
import dk.sdu.cloud.accounting.api.providers.ResourceRetrieveRequest
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.service.SimpleCache
import io.ktor.http.*

interface PathLike<T> {
    val path: String
    fun withNewPath(path: String): T
}

@JvmInline value class InternalFile(override val path: String) : PathLike<InternalFile> {
    override fun withNewPath(path: String): InternalFile = InternalFile(path)
}

/**
 * An internal file where the path is relative to the file-system hosting UCloud/Storage.
 *
 * This path will always start with '/'.
 */
@JvmInline value class RelativeInternalFile(override val path: String) : PathLike<RelativeInternalFile> {
    override fun withNewPath(path: String): RelativeInternalFile = RelativeInternalFile(path)
}

@JvmInline value class UCloudFile private constructor(override val path: String) : PathLike<UCloudFile> {
    override fun withNewPath(path: String): UCloudFile = UCloudFile(path)

    companion object {
        fun create(path: String) = UCloudFile(path.normalize())
        fun createFromPreNormalizedString(path: String) = UCloudFile(path)
    }
}

fun <T : PathLike<T>> T.parent(): T = withNewPath(path.parent())
fun <T : PathLike<T>> T.parents(): List<T> = path.parents().map { withNewPath(it) }
fun <T : PathLike<T>> T.normalize(): T = withNewPath(path.normalize())
fun <T : PathLike<T>> T.components(): List<String> = path.components()
fun <T : PathLike<T>> T.fileName(): String = path.fileName()

class PathConverter(
    private val rootDirectory: InternalFile,
    private val serviceClient: AuthenticatedClient,
) {
    private val collectionCache = SimpleCache<String, FileCollection>(
        maxAge = 60_000 * 10,
        lookup = { collectionId ->
            FileCollectionsControl.retrieve.call(
                ResourceRetrieveRequest(FileCollectionIncludeFlags(), collectionId),
                serviceClient
            ).orThrow()
        }
    )

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun ucloudToInternal(file: UCloudFile): InternalFile {
        val components = file.normalize().components()
        val collectionId = components[0]
        val withoutCollection = components.drop(1)

        val collection = collectionCache.get(collectionId) ?: throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
        val storedName = collection.providerGeneratedId ?: collection.id

        if (storedName.startsWith(COLLECTION_HOME_PREFIX)) {
            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(HOME_DIRECTORY)
                    append('/')
                    append(collectionId.removePrefix(COLLECTION_HOME_PREFIX))
                    for ((idx, component) in withoutCollection.withIndex()) {
                        if (idx == 0) continue
                        append('/')
                        append(component)
                    }
                }
            )
        } else if (storedName.startsWith(COLLECTION_PROJECT_PREFIX)) {
            val (projectId, repository) = collectionToProjectRepositoryOrNull(collectionId)
                ?: throw FSException.NotFound()

            return InternalFile(
                buildString {
                    append(rootDirectory.path)
                    append('/')
                    append(PROJECT_DIRECTORY)
                    append('/')
                    append(projectId)
                    append('/')
                    append(repository)
                    for ((idx, component) in withoutCollection.withIndex()) {
                        if (idx == 0) continue
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
                    for ((idx, component) in withoutCollection.withIndex()) {
                        append('/')
                        append(component)
                    }
                }
            )
        }
    }

    fun projectRepositoryLocation(projectId: String, repository: String): InternalFile {
        return InternalFile(
            buildString {
                append(rootDirectory.path)
                append('/')
                append(PROJECT_DIRECTORY)
                append('/')
                append(projectId)
                append('/')
                append(repository)
            }
        )
    }

    private data class ProjectRepository(val projectId: String, val repository: String)
    private fun collectionToProjectRepositoryOrNull(collection: String): ProjectRepository? {
        if (!collection.startsWith(COLLECTION_PROJECT_PREFIX)) return null
        val withoutPrefix = collection.removePrefix(COLLECTION_PROJECT_PREFIX)
        val splitterIdx = withoutPrefix.indexOfLast { it == '_' }
        if (splitterIdx == -1) throw FSException.NotFound()
        if (splitterIdx == withoutPrefix.length) throw FSException.NotFound()
        val projectId = withoutPrefix.substring(0, splitterIdx)
        val repository = withoutPrefix.substring(splitterIdx + 1)

        return ProjectRepository(projectId, repository)
    }

    fun internalToUCloud(file: InternalFile): UCloudFile {
        val components = file.path.removePrefix(rootDirectory.path).normalize().components()
        if (components.size <= 1) throw FSException.CriticalException("Not a valid UCloud file")

        return UCloudFile.createFromPreNormalizedString(
            buildString {
                append('/')

                val startIdx: Int
                when (components[0]) {
                    HOME_DIRECTORY -> {
                        TODO()
                        append(COLLECTION_HOME_PREFIX)
                        append(components[1])
                        startIdx = 2
                    }

                    PROJECT_DIRECTORY -> {
                        TODO()
                        if (components.size <= 2) throw FSException.CriticalException("Not a valid UCloud file")

                        append(COLLECTION_PROJECT_PREFIX)
                        append(components[1])
                        append("_")
                        append(components[2])
                        startIdx = 3
                    }

                    COLLECTION_DIRECTORY -> {
                        append(components[1])
                        startIdx = 2
                    }

                    else -> throw FSException.CriticalException("Not a valid UCloud file")
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
        const val HOME_DIRECTORY = "home"
        const val PROJECT_DIRECTORY = "projects"
        const val COLLECTION_DIRECTORY = "collections"

        val PRODUCT_REFERENCE = ProductReference("u1-cephfs", "u1-cephfs", UCLOUD_PROVIDER)
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

