package dk.sdu.cloud.plugins.storage.posix

import dk.sdu.cloud.PageV2
import dk.sdu.cloud.ProductBasedConfiguration
import dk.sdu.cloud.accounting.api.ProductReference
import dk.sdu.cloud.accounting.api.providers.ResourceBrowseRequest
import dk.sdu.cloud.calls.BulkRequest
import dk.sdu.cloud.calls.BulkResponse
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.calls.bulkResponseOf
import dk.sdu.cloud.file.orchestrator.api.*
import dk.sdu.cloud.plugins.FilePlugin
import dk.sdu.cloud.plugins.PluginContext
import dk.sdu.cloud.plugins.storage.InternalFile
import dk.sdu.cloud.plugins.storage.PathConverter
import dk.sdu.cloud.plugins.storage.UCloudFile
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.service.Logger
import dk.sdu.cloud.utils.NativeFile
import dk.sdu.cloud.utils.NativeFileException
import io.ktor.http.*
import kotlinx.cinterop.*
import platform.posix.*

class PosixFilesPlugin : FilePlugin {
    private lateinit var pathConverter: PathConverter

    override suspend fun PluginContext.initialize(pluginConfig: ProductBasedConfiguration) {
        pathConverter = PathConverter(this)
    }

    override suspend fun PluginContext.createFolder(
        req: BulkRequest<FilesProviderCreateFolderRequestItem>
    ): BulkResponse<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            val internalFile = pathConverter.ucloudToInternal(UCloudFile.create(reqItem.id))

            val err = mkdir(internalFile.path, DEFAULT_DIR_MODE)
            if (err < 0) {
                log.debug("Could not create directories at ${internalFile.path}")
                throw RPCException.fromStatusCode(HttpStatusCode.NotFound)
            }
            null
        }
        return BulkResponse(result)
    }

    private fun IntArray.closeAll() {
        for (descriptor in this) {
            if (descriptor > 0) {
                close(descriptor)
            }
        }
    }

    private fun createAccordingToPolicy(
        parent: InternalFile,
        desiredFileName: String,
        conflictPolicy: WriteConflictPolicy,
        isDirectory: Boolean,
        truncate: Boolean = true,
    ): Pair<String, Int> {
        val mode = if (isDirectory) DEFAULT_DIR_MODE else DEFAULT_FILE_MODE
        val fixedConflictPolicy = conflictPolicy

        fun createDirAndOpen(name: String): Pair<String, Int>? {
            // If it doesn't exist everything is good. Create the directory and return the name + fd.
            val status = mkdir(parent.path + "/" + name, DEFAULT_DIR_MODE)
            if (status >= 0) {
                val fd = open(parent.path + "/" + name, 0, DEFAULT_DIR_MODE)
                if (fd >= 0) return Pair(parent.path + "/" + name, fd)

                // Very unexpected, but technically possible. Fall through to the naming step.
            }

            // The name was taken before we could complete our operation. Fall through to naming step.
            return null
        }

        var oflags = 0
        if (!isDirectory) {
            oflags = oflags or O_CREAT or O_WRONLY
            if (truncate) oflags = oflags or O_TRUNC
            if (fixedConflictPolicy != WriteConflictPolicy.REPLACE) oflags = oflags or O_EXCL
        } else {
            oflags = oflags or O_DIRECTORY
        }

        val desiredFd = open(parent.path + "/" + desiredFileName, oflags, mode)
        println("PATH " + parent.path + "/" + desiredFileName)
        println("FLAGS: $oflags" )
        println("MODE : " + mode)
        if (!isDirectory) {
            if (desiredFd >= 0) return Pair(parent.path + "/" +desiredFileName, desiredFd)
        } else {
            // If it exists and we allow overwrite then just return the open directory
            if (
                (fixedConflictPolicy == WriteConflictPolicy.REPLACE || fixedConflictPolicy == WriteConflictPolicy.MERGE_RENAME) &&
                desiredFd >= 0
            ) {
                return Pair(parent.path + "/" +desiredFileName, desiredFd)
            } else if (desiredFd < 0) {
                val result = createDirAndOpen(desiredFileName)
                if (result != null) return result
            } else {
                close(desiredFd) // We don't need this one
            }

            // We need to create a differently named directory (see below)
        }

        if (fixedConflictPolicy == WriteConflictPolicy.REJECT) throw RPCException.fromStatusCode(HttpStatusCode.Conflict)
        check(fixedConflictPolicy == WriteConflictPolicy.RENAME || fixedConflictPolicy == WriteConflictPolicy.MERGE_RENAME)

        for (attempt in 1 until 10_000) { // NOTE(Dan): We put an upper-limit to avoid looping 'forever'
            val filenameWithoutExtension = desiredFileName.substringBeforeLast('.')
            val extension = desiredFileName.substringAfterLast('.')
            val hasExtension = desiredFileName.length != filenameWithoutExtension.length

            val newName = buildString {
                append(filenameWithoutExtension)
                append("(")
                append(attempt)
                append(")")
                if (hasExtension) {
                    append('.')
                    append(extension)
                }
            }
            val attemptedFd = open(parent.path + "/" + newName, oflags, mode)
            if (!isDirectory) {
                if (attemptedFd >= 0) return Pair(parent.path + "/" + newName, attemptedFd)
            } else {
                val result = createDirAndOpen(newName)
                if (result != null) return result
            }
        }

        throw RPCException.fromStatusCode(HttpStatusCode.BadRequest, "Too many files with this name exist: '$desiredFileName'")
    }

    data class MoveShouldContinue(val needsToRecurse: Boolean)

    private fun move(
        source: InternalFile,
        destination: InternalFile,
        conflictPolicy: WriteConflictPolicy
    ): MoveShouldContinue {
        val sourceStat = nativeStat(source)
        var shouldContinue = false

        val desiredFileName = destination.path.fileName()
        if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && sourceStat.status.type == FileType.DIRECTORY) {
            val destFd = open(desiredFileName, 0, 0)
            if (destFd >= 0) {
                shouldContinue = true
                close(destFd)
            }
        }

        val (destinationName, destinationFd) = createAccordingToPolicy(
            InternalFile(destination.path.parent()),
            desiredFileName,
            conflictPolicy,
            sourceStat.status.type == FileType.DIRECTORY,
        )
        close(destinationFd)

        if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && desiredFileName == destinationName &&
            sourceStat.status.type == FileType.DIRECTORY
        ) {
            // NOTE(Dan): Do nothing. The function above has potentially re-used an existing directory which
            // might not be empty. The `renameat` call will fail for non-empty directories which is not what we
            // want in this specific instance.
        } else {
            val err = rename(
                source.path,
                destinationName
            )

            if (err < 0) {
                throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError, "failed with $errno")
            }
        }
        return MoveShouldContinue(shouldContinue)
    }

    override suspend fun PluginContext.move(
        req: BulkRequest<FilesProviderMoveRequestItem>
    ): BulkResponse<LongRunningTask?> {
        val result = req.items.map { reqItem ->
            val source = UCloudFile.create(reqItem.oldId)
            val destination = UCloudFile.create(reqItem.newId)
            val conflictPolicy = reqItem.conflictPolicy
            val needsToRecurse = move(
                pathConverter.ucloudToInternal(source),
                pathConverter.ucloudToInternal(destination),
                conflictPolicy
            ).needsToRecurse
            if (needsToRecurse) {
                val files = browse(
                    UCloudFile.create(source.path),
                    FilesProviderBrowseRequest(
                        pathConverter.ucloudToCollection(UCloudFile.create(source.path)),
                        ResourceBrowseRequest(
                            UFileIncludeFlags()
                        )
                    )
                )
                val requests = files.items.map { file ->
                    FilesProviderMoveRequestItem(
                        reqItem.resolvedOldCollection,
                        reqItem.resolvedNewCollection,
                        source.path + "/" + file.id,
                        destination.path + "/" + file.id,
                        conflictPolicy
                    )
                }
                move(BulkRequest(requests))
            }
            null
        }

        return BulkResponse(result)
    }

    override suspend fun PluginContext.browse(
        path: UCloudFile,
        request: FilesProviderBrowseRequest
    ): PageV2<PartialUFile> {
        val internalFile = pathConverter.ucloudToInternal(path)
        val openedDirectory = try {
            NativeFile.open(internalFile.path, readOnly = true, createIfNeeded = false)
        } catch (ex: NativeFileException) {
            println(internalFile.path)
            println(ex.stackTraceToString())
            throw RPCException("File not found", HttpStatusCode.NotFound)
        }
        try {
            val dir = fdopendir(openedDirectory.fd)
                ?: throw RPCException("File is not a directory", HttpStatusCode.Conflict)

            val result = ArrayList<PartialUFile>()
            while (true) {
                val ent = readdir(dir) ?: break
                val name = ent.pointed.d_name.toKString()
                if (name == "." || name == "..") continue
                runCatching {
                    // NOTE(Dan): Ignore errors, in case the file is being changed while we inspect it
                    result.add(nativeStat(InternalFile(internalFile.path + "/" + name)))
                }
            }
            closedir(dir)

            return PageV2(result.size, result, null)
        } finally {
            openedDirectory.close()
        }
    }

    private fun nativeStat(file: InternalFile): PartialUFile {
        return memScoped {
            val st = alloc<stat>()
            val error = stat(file.path, st.ptr)
            if (error < 0) {
                // TODO actually remap the error code
                throw RPCException("Could not open file", HttpStatusCode.NotFound)
            }

            val modifiedAt = (st.st_mtim.tv_sec * 1000) + (st.st_mtim.tv_nsec / 1_000_000)
            PartialUFile(
                pathConverter.internalToUCloud(file).path,
                UFileStatus(
                    if (st.st_mode and S_ISREG == 0U) FileType.DIRECTORY else FileType.FILE,
                    sizeInBytes = st.st_size,
                    modifiedAt = modifiedAt,
                    unixOwner = st.st_uid.toInt(),
                    unixGroup = st.st_gid.toInt(),
                    unixMode = st.st_mode.toInt(),
                ),
                modifiedAt
            )
        }
    }

    override suspend fun PluginContext.retrieve(request: FilesProviderRetrieveRequest): PartialUFile {
        return nativeStat(pathConverter.ucloudToInternal(UCloudFile.create(request.retrieve.id)))
    }

    override suspend fun PluginContext.delete(resource: UFile) {
        TODO("Not yet implemented")
    }

    override suspend fun PluginContext.retrieveProducts(
        knownProducts: List<ProductReference>
    ): BulkResponse<FSSupport> {
        return BulkResponse(knownProducts.map {
            FSSupport(
                it,
                FSProductStatsSupport(),
                FSCollectionSupport(),
                FSFileSupport()
            )
        })
    }

    companion object: Loggable {
        override val log: Logger = logger()

        private const val S_ISREG = 0x8000U
        const val DEFAULT_DIR_MODE = 488U // 0750
        const val DEFAULT_FILE_MODE = 416U // 0640
    }
}
