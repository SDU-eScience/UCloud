package dk.sdu.cloud.file.ucloud.services

import com.sun.jna.Native
import com.sun.jna.Platform
import dk.sdu.cloud.calls.HttpStatusCode
import dk.sdu.cloud.calls.RPCException
import dk.sdu.cloud.debug.DebugSystem
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.file.orchestrator.api.WriteConflictPolicy
import dk.sdu.cloud.file.orchestrator.api.joinPath
import dk.sdu.cloud.micro.Micro
import dk.sdu.cloud.micro.feature
import dk.sdu.cloud.service.Loggable
import io.ktor.utils.io.pool.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

data class NativeStat(
    val size: Long,
    val modifiedAt: Long,
    val fileType: FileType,
    val ownerUid: Int,
    val ownerGid: Int,
    val mode: Int,
)

sealed class CopyResult {
    object CreatedFile : CopyResult()
    class CreatedDirectory(val outputFile: InternalFile) : CopyResult()
    object NothingToCreate : CopyResult()
}

const val LINUX_FS_USER_UID = 11042

class NativeFS(
    private val pathConverter: PathConverter,
    private val micro: Micro? = null,
) {
    var disableChown = false
    private val debug: DebugSystem? = micro?.feature(DebugSystem)

    private fun openFile(file: InternalFile, flag: Int = 0): LinuxFileHandle {
        with(CLibrary.INSTANCE) {
            val components = file.components()
            val fileDescriptors = Array<LinuxFileHandle?>(components.size) { null }
            try {
                fileDescriptors[0] = LinuxFileHandle.createOrThrow(open("/${components[0]}", O_NOFOLLOW, 0)) {
                    throw FSException.NotFound(
                        // NOTE(Dan): This might crash if the internal collection doesn't exist (yet)
                        runCatching { pathConverter.internalToUCloud(file).path }.getOrNull()
                    )
                }

                for (i in 1 until fileDescriptors.size) {
                    val previousFd = fileDescriptors[i - 1] ?: error("Should never happen")

                    val opts =
                        if (i == fileDescriptors.lastIndex) O_NOFOLLOW or flag
                        else O_NOFOLLOW
                    fileDescriptors[i] =
                        LinuxFileHandle.createOrThrow(openat(previousFd.fd, components[i], opts, DEFAULT_FILE_MODE)) {
                            throw FSException.NotFound(
                                // NOTE(Dan): This might crash if the internal collection doesn't exist (yet)
                                runCatching { pathConverter.internalToUCloud(file).path }.getOrNull()
                            )
                        }
                    previousFd.close()
                    fileDescriptors[i - 1] = null
                }
            } catch (ex: Throwable) {
                fileDescriptors.closeAll()
                throw ex
            }

            return fileDescriptors.last() ?: throwExceptionBasedOnStatus(Native.getLastError())
        }
    }

    private fun Array<LinuxFileHandle?>.closeAll() {
        for (descriptor in this) {
            descriptor?.close()
        }
    }

    fun copy(
        source: InternalFile,
        destination: InternalFile,
        conflictPolicy: WriteConflictPolicy,
        owner: Int? = LINUX_FS_USER_UID,
    ): CopyResult {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val sourceFd = openFile(source)
                val parentFile = destination.parent()
                val destParentFd = openFile(parentFile)
                try {
                    val sourceStat = nativeStat(sourceFd, autoClose = false)
                    val fileName = destination.fileName()
                    if (sourceStat.fileType == FileType.FILE) {
                        val (destFilename, destFd) = createAccordingToPolicy(
                            destParentFd,
                            fileName,
                            conflictPolicy,
                            internalDestination = destination,
                            isDirectory = false
                        )

                        val ins = LinuxInputStream(sourceFd) // Closed later
                        LinuxOutputStream(destFd).use { outs ->
                            ins.copyTo(outs)
                            fchmod(destFd.fd, sourceStat.mode)
                        }
                        return CopyResult.CreatedFile
                    } else if (sourceStat.fileType == FileType.DIRECTORY) {
                        val result = createAccordingToPolicy(
                            destParentFd,
                            fileName,
                            conflictPolicy,
                            internalDestination = destination,
                            isDirectory = true
                        )

                        try {
                            if (owner != null) fchown(result.second.fd, owner, owner)
                        } finally {
                            result.second.close()
                        }

                        return CopyResult.CreatedDirectory(
                            InternalFile(joinPath(parentFile.path.removeSuffix("/"), result.first).removeSuffix("/"))
                        )
                    } else {
                        return CopyResult.NothingToCreate
                    }
                } finally {
                    sourceFd.close()
                    destParentFd.close()
                }
            }
        } else {
            val file = File(source.path)
            Files.copy(
                file.toPath(),
                File(destination.path).toPath(),
                *(if (conflictPolicy == WriteConflictPolicy.REPLACE) arrayOf(StandardCopyOption.REPLACE_EXISTING)
                else emptyArray())
            )
            return if (file.isDirectory) CopyResult.CreatedDirectory(destination)
            else CopyResult.CreatedFile
        }
    }

    fun listFiles(file: InternalFile): List<String> {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val handle = openFile(file)

                val dir = fdopendir(handle.fd)
                if (dir == null) {
                    handle.close()
                    throw FSException.IsDirectoryConflict()
                }

                val result = ArrayList<String>()
                while (true) {
                    val ent = readdir(dir) ?: break
                    // Read unsized string at end of struct. The ABI for this function leaves the size completely
                    // undefined.
                    val name = ent.pointer.getString(19) // 19 (bytes) is the offset in the struct
                    if (name == "." || name == "..") continue
                    result.add(name)
                }

                closedir(dir) // no need for close(fd) since closedir(dir) already does this
                return result
            }
        } else {
            return File(file.path).list()?.toList() ?: emptyList()
        }
    }

    private fun nativeStat(handle: LinuxFileHandle, autoClose: Boolean): NativeStat {
        val st = stat()
        st.write()
        val err = CLibrary.INSTANCE.__fxstat64(1, handle.fd, st.pointer)
        st.read()

        if (autoClose) {
            handle.close()
        }
        if (err < 0) {
            throw FSException.NotFound()
        }

        return NativeStat(
            st.st_size,
            (st.m_sec * 1000) + (st.m_nsec / 1_000_000),
            if (st.st_mode and S_ISREG == 0) FileType.DIRECTORY else FileType.FILE,
            st.st_uid,
            st.st_gid,
            st.st_mode
        )
    }

    private fun createAccordingToPolicy(
        parentFd: LinuxFileHandle,
        desiredFileName: String,
        conflictPolicy: WriteConflictPolicy,
        isDirectory: Boolean,
        internalDestination: InternalFile,
        truncate: Boolean = true,
    ): Pair<String, LinuxFileHandle> {
        val mode = if (isDirectory) DEFAULT_DIR_MODE else DEFAULT_FILE_MODE
        val fixedConflictPolicy = run {
            if (conflictPolicy != WriteConflictPolicy.RENAME) {
                conflictPolicy
            } else {
                val relativeFile = pathConverter.internalToRelative(internalDestination)
                val components = relativeFile.components()
                if (isPersonalWorkspace(relativeFile)) {
                    // /home/$USERNAME should never be renamed
                    if (relativeFile.components().size == 2) {
                        WriteConflictPolicy.REJECT
                    } else {
                        conflictPolicy
                    }
                } else if (isProjectWorkspace(relativeFile)) {
                    // /projects/$PROJECT/$REPO
                    // /projects/$PROJECT
                    // Neither should be renamed

                    val components = relativeFile.components()
                    if (components.size == 2 || components.size == 3) {
                        WriteConflictPolicy.REJECT
                    } else {
                        conflictPolicy
                    }
                } else if (components[0] == PathConverter.COLLECTION_DIRECTORY) {
                    if (components.size == 1) WriteConflictPolicy.REJECT
                    else conflictPolicy
                } else {
                    throw RPCException("Unexpected file", HttpStatusCode.InternalServerError)
                }
            }
        }

        fun createDirAndOpen(name: String): Pair<String, LinuxFileHandle>? {
            // If it doesn't exist everything is good. Create the directory and return the name + fd.
            val status = CLibrary.INSTANCE.mkdirat(parentFd.fd, name, DEFAULT_DIR_MODE)
            if (status >= 0) {
                val fd = LinuxFileHandle.createOrNull(
                    CLibrary.INSTANCE.openat(parentFd.fd, name, O_NOFOLLOW, DEFAULT_DIR_MODE)
                )
                if (fd != null) return Pair(name, fd)

                // Very unexpected, but technically possible. Fall through to the naming step.
            }

            // The name was taken before we could complete our operation. Fall through to naming step.
            return null
        }

        var oflags = O_NOFOLLOW
        if (!isDirectory) {
            oflags = oflags or O_CREAT or O_WRONLY
            if (truncate) oflags = oflags or O_TRUNC
            if (fixedConflictPolicy != WriteConflictPolicy.REPLACE) oflags = oflags or O_EXCL
        } else {
            oflags = oflags or O_DIRECTORY
        }

        val desiredFd = LinuxFileHandle.createOrNull(
            CLibrary.INSTANCE.openat(parentFd.fd, desiredFileName, oflags, mode)
        )
        if (!isDirectory) {
            if (desiredFd != null) return Pair(desiredFileName, desiredFd)
        } else {
            // If it exists and we allow overwrite then just return the open directory
            if (
                (fixedConflictPolicy == WriteConflictPolicy.REPLACE || fixedConflictPolicy == WriteConflictPolicy.MERGE_RENAME) &&
                desiredFd != null
            ) {
                return Pair(desiredFileName, desiredFd)
            } else if (desiredFd == null) {
                val result = createDirAndOpen(desiredFileName)
                if (result != null) return result
            } else {
                desiredFd.close() // We don't need this one
            }

            // We need to create a differently named directory (see below)
        }

        if (fixedConflictPolicy == WriteConflictPolicy.REJECT) throw FSException.AlreadyExists()
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
            val attemptedFd = LinuxFileHandle.createOrNull(CLibrary.INSTANCE.openat(parentFd.fd, newName, oflags, mode))
            if (!isDirectory) {
                if (attemptedFd != null) return Pair(newName, attemptedFd)
                val errno = Native.getLastError()
                if (errno != EEXIST) {
                    // NOTE(Dan): This is unexpected error, bail out completely
                    log.warn("Received an unexpected error code while renaming item: $errno " +
                        "($parentFd, $desiredFileName, $conflictPolicy, $isDirectory, $internalDestination, $truncate)")
                    throw RPCException.fromStatusCode(HttpStatusCode.InternalServerError)
                }
            } else {
                val result = createDirAndOpen(newName)
                if (result != null) return result
            }
        }

        throw FSException.BadRequest("Too many files with this name exist: '$desiredFileName'")
    }

    private fun setMetadataForNewFile(
        handle: LinuxFileHandle,
        owner: Int? = LINUX_FS_USER_UID,
        permissions: Int?,
    ) {
        if (owner != null) CLibrary.INSTANCE.fchown(handle.fd, owner, owner)
        if (permissions != null) CLibrary.INSTANCE.fchmod(handle.fd, permissions)
    }

    fun openForWriting(
        file: InternalFile,
        conflictPolicy: WriteConflictPolicy,
        owner: Int? = LINUX_FS_USER_UID,
        permissions: Int? = DEFAULT_FILE_MODE,
        truncate: Boolean = true,
        offset: Long? = null,
    ): Pair<String, OutputStream> {
        if (Platform.isLinux()) {
            val parentFd = openFile(file.parent())
            try {
                val (targetName, targetFd) = createAccordingToPolicy(
                    parentFd,
                    file.fileName(),
                    conflictPolicy,
                    internalDestination = file,
                    isDirectory = false,
                    truncate = truncate
                )

                if (offset != null) {
                    if (!truncate) {
                        CLibrary.INSTANCE.lseek(targetFd.fd, offset, SEEK_SET)
                    } else {
                        error("truncate = true with offset != null")
                    }
                } else {
                    if (!truncate) {
                        CLibrary.INSTANCE.lseek(targetFd.fd, 0, SEEK_END)
                    }
                }

                setMetadataForNewFile(targetFd, owner, permissions)
                return Pair(targetName, LinuxOutputStream(targetFd))
            } finally {
                parentFd.close()
            }
        } else {
            val options = HashSet<OpenOption>()
            options.add(StandardOpenOption.TRUNCATE_EXISTING)
            options.add(StandardOpenOption.WRITE)
            options.add(LinkOption.NOFOLLOW_LINKS)
            if (conflictPolicy != WriteConflictPolicy.REPLACE) {
                options.add(StandardOpenOption.CREATE_NEW)
            } else {
                options.add(StandardOpenOption.CREATE)
            }

            try {
                val systemPath = File(file.path).toPath()
                return Pair(file.fileName(), Channels.newOutputStream(
                    Files.newByteChannel(
                        systemPath,
                        options,
                        PosixFilePermissions.asFileAttribute(DEFAULT_POSIX_FILE_MODE)
                    ).also {
                        if (offset != null) {
                            it.position(offset)
                        }
                    }
                ))
            } catch (ex: FileAlreadyExistsException) {
                throw FSException.AlreadyExists()
            } catch (ex: Throwable) {
                if (ex.message?.contains("Is a directory") == true) {
                    throw FSException.BadRequest("Upload target is a not a directory")
                } else {
                    throw ex
                }
            }
        }
    }

    fun openForReading(file: InternalFile): InputStream {
        return if (Platform.isLinux()) {
            LinuxInputStream(openFile(file, O_RDONLY)).buffered()
        } else {
            FileInputStream(file.path)
        }
    }

    fun delete(file: InternalFile, allowRecursion: Boolean = true) {
        if (Platform.isLinux()) {
            val fd = openFile(file.parent())
            try {
                if (CLibrary.INSTANCE.unlinkat(fd.fd, file.fileName(), AT_REMOVEDIR) < 0) {
                    if (CLibrary.INSTANCE.unlinkat(fd.fd, file.fileName(), 0) < 0) {
                        if (Native.getLastError() == ENOTEMPTY) {
                            throw FSException.BadRequest()
                        }
                        if (Native.getLastError() == EISDIR && allowRecursion) {
                            log.debug("Is directory - should traverse")
                            listFiles(file).forEach { path ->
                                delete(InternalFile(file.path+"/"+path))
                            }
                            delete(file)
                        } else {
                            throw FSException.NotFound()
                        }
                    }
                }
            } finally {
                fd.close()
            }
        } else {
            Files.delete(File(file.path).toPath())
        }
    }

    fun getExtendedAttribute(file: InternalFile, attribute: String): String {
        return if (Platform.isLinux()) {
            val handle = openFile(file)
            try {
                getExtendedAttribute(handle.fd, attribute)
            } finally {
                handle.close()
            }
        } else {
            DefaultByteArrayPool.useInstance {
                val read = XAttrOSX.INSTANCE.getxattr(file.path, attribute, it, it.size, 0, 0)
                if (read < 0) throw NativeException(Native.getLastError())
                String(it, 0, read, Charsets.UTF_8)
            }
        }
    }

    private fun getExtendedAttribute(fd: Int, attribute: String): String {
        require(Platform.isLinux())
        return DefaultByteArrayPool.useInstance { buf ->
            val xattrSize = CLibrary.INSTANCE.fgetxattr(fd, attribute, buf, buf.size)
            if (xattrSize < 0) throw NativeException(Native.getLastError())
            String(buf, 0, xattrSize, Charsets.UTF_8)
        }
    }

    /*
    fun setExtendedAttribute(path: File, attribute: String, value: String, allowOverwrite: Boolean): Int {
        val bytes = value.toByteArray()

        if (Platform.isLinux()) {
            val fd = openFile(path.absolutePath)
            try {
                return CLibrary.INSTANCE.fsetxattr(fd, attribute, bytes, bytes.size, if (!allowOverwrite) 1 else 0)
            } finally {
                CLibrary.INSTANCE.close(fd)
            }
        } else {
            return XAttrOSX.INSTANCE.setxattr(
                path.absolutePath,
                attribute,
                bytes,
                bytes.size,
                0,
                if (!allowOverwrite) 2 else 0
            )
        }
    }

    fun removeExtendedAttribute(path: File, attribute: String) {
        if (Platform.isLinux()) {
            val fd = openFile(path.absolutePath)
            try {
                CLibrary.INSTANCE.fremovexattr(fd, attribute)
            } finally {
                CLibrary.INSTANCE.close(fd)
            }
        } else {
            XAttrOSX.INSTANCE.removexattr(path.absolutePath, attribute, 0)
        }
    }
     */


    fun readNativeFilePermissons(file: InternalFile): Int {
        return if (Platform.isLinux()) {
            val handle = openFile(file)
            val st = stat()
            st.write()
            val err = CLibrary.INSTANCE.__fxstat64(1, handle.fd, st.pointer)
            st.read()
            handle.close()
            if (err < 0) {
                throw FSException.NotFound()
            }
            st.st_mode
        } else {
            // rw-rw-rw- in octal
            304472
        }
    }

    fun stat(file: InternalFile): NativeStat {
        if (Platform.isLinux()) {
            val fd = openFile(file)
            return nativeStat(fd, autoClose = true)
        } else {
            if (Files.isSymbolicLink(File(file.path).toPath())) throw FSException.NotFound()

            val basicAttributes = run {
                val opts = listOf("size", "lastModifiedTime", "isDirectory")
                Files.readAttributes(File(file.path).toPath(), opts.joinToString(","), LinkOption.NOFOLLOW_LINKS)
            }

            val size = basicAttributes.getValue("size") as Long
            val fileType = if (basicAttributes.getValue("isDirectory") as Boolean) {
                FileType.DIRECTORY
            } else {
                FileType.FILE
            }

            val modifiedAt = (basicAttributes.getValue("lastModifiedTime") as FileTime).toMillis()
            return NativeStat(size, modifiedAt, fileType, LINUX_FS_USER_UID, LINUX_FS_USER_UID, DEFAULT_FILE_MODE)
        }
    }


    fun chmod(file: InternalFile, mode: Int) {
        if (!Platform.isLinux()) return
        val handle = openFile(file)
        try {
            CLibrary.INSTANCE.fchmod(handle.fd, mode)
        } finally {
            handle.close()
        }
    }

    fun chown(file: InternalFile, uid: Int, gid: Int) {
        if (!Platform.isLinux() || disableChown) return
        val handle = openFile(file)
        try {
            CLibrary.INSTANCE.fchown(handle.fd, uid, gid)
        } finally {
            handle.close()
        }
    }

    fun changeFilePermissions(file: InternalFile, mode: Int, uid: Int, gid: Int) {
        if (!Platform.isLinux()) return
        val handle = openFile(file)
        try {
            CLibrary.INSTANCE.fchmod(handle.fd, mode)
            CLibrary.INSTANCE.fchown(handle.fd, uid, gid)
        } finally {
            handle.close()
        }
    }

    fun createDirectories(file: InternalFile, owner: Int? = LINUX_FS_USER_UID) {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val components = file.components()
                if (components.isEmpty()) throw IllegalArgumentException("Path is empty")

                val fileDescriptors = Array<LinuxFileHandle?>(components.size - 1) { null }
                var didCreatePrevious = false
                try {
                    fileDescriptors[0] = LinuxFileHandle.createOrThrow(open("/${components[0]}", O_NOFOLLOW, 0)) {
                        throw FSException.NotFound()
                    }
                    var i = 1
                    while (i < fileDescriptors.size) {
                        val previousFd = fileDescriptors[i - 1] ?: error("Should never happen")

                        if (didCreatePrevious && owner != null) {
                            fchown(previousFd.fd, owner, owner)
                            didCreatePrevious = false
                        }

                        fileDescriptors[i] = LinuxFileHandle.createOrNull(
                            openat(previousFd.fd, components[i], O_NOFOLLOW, 0)
                        )

                        if (fileDescriptors[i] == null && Native.getLastError() == ENOENT) {
                            val err = mkdirat(previousFd.fd, components[i], DEFAULT_DIR_MODE)
                            if (err < 0) {
                                log.debug("Could not create directories at $file")
                                throw FSException.NotFound()
                            }
                            didCreatePrevious = true
                        } else {
                            i++
                        }
                    }

                    val finalFd = fileDescriptors.last() ?: throwExceptionBasedOnStatus(Native.getLastError())

                    val error = mkdirat(finalFd.fd, components.last(), DEFAULT_DIR_MODE)
                    if (error != 0) {
                        throwExceptionBasedOnStatus(Native.getLastError())
                    }

                    if (owner != null) {
                        val handle = LinuxFileHandle.createOrNull(openat(finalFd.fd, components.last(), 0, 0))
                        if (handle != null) {
                            fchown(handle.fd, owner, owner)
                            handle.close()
                        }
                    }
                } finally {
                    fileDescriptors.closeAll()
                }
            }
        } else {
            File(file.path).mkdirs()
        }
    }

    data class MoveShouldContinue(val needsToRecurse: Boolean)

    fun move(source: InternalFile, destination: InternalFile, conflictPolicy: WriteConflictPolicy): MoveShouldContinue {
        if (Platform.isLinux()) {
            val sourceParent = openFile(source.parent())
            val destinationParent = openFile(destination.parent())

            try {
                val sourceFd = LinuxFileHandle.createOrThrow(
                    CLibrary.INSTANCE.openat(sourceParent.fd, source.fileName(), 0, DEFAULT_FILE_MODE)
                ) { throw FSException.NotFound() }
                val sourceStat = nativeStat(sourceFd, autoClose = true)
                var shouldContinue = false

                val desiredFileName = destination.fileName()
                if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && sourceStat.fileType == FileType.DIRECTORY) {
                    val destFd = LinuxFileHandle.createOrNull(
                        CLibrary.INSTANCE.openat(destinationParent.fd, desiredFileName, 0, 0)
                    )
                    if (destFd != null) {
                        shouldContinue = true
                        destFd.close()
                    }
                }

                // NOTE(Dan): This call will create the file if it doesn't already exist. This is typically not a
                // problem, since it will be replaced by the `renameat` call.
                val (destinationName, destinationFd) = createAccordingToPolicy(
                    destinationParent,
                    desiredFileName,
                    conflictPolicy,
                    sourceStat.fileType == FileType.DIRECTORY,
                    internalDestination = destination
                )
                destinationFd.close()

                if (conflictPolicy == WriteConflictPolicy.MERGE_RENAME && desiredFileName == destinationName &&
                    sourceStat.fileType == FileType.DIRECTORY
                ) {
                    // NOTE(Dan): Do nothing. The function above has potentially re-used an existing directory which
                    // might not be empty. The `renameat` call will fail for non-empty directories which is not what we
                    // want in this specific instance.
                } else {
                    val err = CLibrary.INSTANCE.renameat(
                        sourceParent.fd,
                        source.fileName(),
                        destinationParent.fd,
                        destinationName
                    )

                    if (err < 0) throwExceptionBasedOnStatus(Native.getLastError())
                }
                return MoveShouldContinue(shouldContinue)
            } finally {
                sourceParent.close()
                destinationParent.close()
            }
        } else {
            val opts = run {
                val extraOpts: Array<CopyOption> =
                    if (conflictPolicy == WriteConflictPolicy.REPLACE) arrayOf(StandardCopyOption.REPLACE_EXISTING)
                    else emptyArray()

                extraOpts + arrayOf(LinkOption.NOFOLLOW_LINKS)
            }

            Files.move(File(source.path).toPath(), File(destination.path).toPath(), *opts)
            return MoveShouldContinue(false)
        }
    }

    companion object : Loggable {
        private const val O_NOFOLLOW = 0x20000
        private const val O_TRUNC = 0x200
        private const val O_CREAT = 0x40
        private const val O_EXCL = 0x80
        private const val O_WRONLY = 0x1
        private const val O_RDONLY = 0x0
        private const val O_DIRECTORY = 0x10000
        private const val ENOENT = 2
        private const val ELOOP = 40
        private const val EEXIST = 17
        private const val EISDIR = 21
        private const val ENOTEMPTY = 39
        const val DEFAULT_DIR_MODE = 488 // 0750
        const val DEFAULT_FILE_MODE = 416 // 0640
        private const val AT_REMOVEDIR = 0x200
        private const val S_ISREG = 0x8000
        private const val SEEK_SET = 0
        private const val SEEK_CUR = 1
        private const val SEEK_END = 2

        override val log = logger()
    }
}

class NativeException(val statusCode: Int) : RuntimeException("Native exception, code: $statusCode")

val DEFAULT_POSIX_FILE_MODE = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.GROUP_WRITE
)

val DEFAULT_POSIX_DIRECTORY_MODE = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OWNER_EXECUTE,
    PosixFilePermission.GROUP_READ,
    PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.GROUP_EXECUTE,
    PosixFilePermission.OTHERS_EXECUTE
)
