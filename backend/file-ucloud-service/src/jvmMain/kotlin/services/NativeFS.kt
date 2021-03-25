package dk.sdu.cloud.file.ucloud.services

import com.sun.jna.Native
import com.sun.jna.Platform
import dk.sdu.cloud.file.orchestrator.api.FileType
import dk.sdu.cloud.service.Loggable
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
    val mode: Int
)

enum class CopyResult {
    CREATED_FILE,
    CREATED_DIRECTORY,
    NOTHING_TO_CREATE,
}

const val LINUX_FS_USER_UID = 11042

object NativeFS : Loggable {
    private const val O_NOFOLLOW = 0x20000
    private const val O_TRUNC = 0x200
    private const val O_CREAT = 0x40
    private const val O_EXCL = 0x80
    private const val O_WRONLY = 0x1
    private const val O_RDONLY = 0x0
    private const val ENOENT = 2
    private const val ENOTEMPTY = 39
    const val DEFAULT_DIR_MODE = 488 // 0750
    const val DEFAULT_FILE_MODE = 416 // 0640
    private const val AT_REMOVEDIR = 0x200
    private const val S_ISREG = 0x8000

    var disableChown = false

    override val log = logger()

    private fun openFile(file: InternalFile, flag: Int = 0): Int {
        with(CLibrary.INSTANCE) {
            val components = file.components()
            val fileDescriptors = IntArray(components.size) { -1 }
            try {
                fileDescriptors[0] = open("/${components[0]}", O_NOFOLLOW, 0)
                for (i in 1 until fileDescriptors.size) {
                    val previousFd = fileDescriptors[i - 1]
                    if (previousFd < 0) {
                        throw FSException.NotFound()
                    }

                    val opts =
                        if (i == fileDescriptors.lastIndex) O_NOFOLLOW or flag
                        else O_NOFOLLOW
                    fileDescriptors[i] = openat(fileDescriptors[i - 1], components[i], opts, DEFAULT_FILE_MODE)
                    close(previousFd)
                }
            } catch (ex: Throwable) {
                fileDescriptors.closeAll()
                throw ex
            }

            if (fileDescriptors.last() < 0) {
                throwExceptionBasedOnStatus(Native.getLastError())
            }

            return fileDescriptors.last()
        }
    }

    private fun IntArray.closeAll() {
        for (descriptor in this) {
            if (descriptor > 0) {
                CLibrary.INSTANCE.close(descriptor)
            }
        }
    }

    fun createDirectories(file: InternalFile, owner: Int? = LINUX_FS_USER_UID) {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val components = file.components()
                if (components.isEmpty()) throw IllegalArgumentException("Path is empty")

                val fileDescriptors = IntArray(components.size - 1) { -1 }
                var didCreatePrevious = false
                try {
                    fileDescriptors[0] = open("/${components[0]}", O_NOFOLLOW, 0)
                    var i = 1
                    while (i < fileDescriptors.size) {
                        val previousFd = fileDescriptors[i - 1]
                        if (previousFd < 0) {
                            throw FSException.NotFound()
                        }

                        if (didCreatePrevious && owner != null) {
                            fchown(previousFd, owner, owner)
                            didCreatePrevious = false
                        }

                        fileDescriptors[i] = openat(fileDescriptors[i - 1], components[i], O_NOFOLLOW, 0)

                        if (fileDescriptors[i] < 0 && Native.getLastError() == ENOENT) {
                            val err = mkdirat(fileDescriptors[i - 1], components[i], DEFAULT_DIR_MODE)
                            if (err < 0) throw FSException.NotFound()
                        } else {
                            i++
                        }
                    }

                    val finalFd = fileDescriptors.last()
                    if (finalFd < 0) throwExceptionBasedOnStatus(Native.getLastError())

                    val error = mkdirat(finalFd, components.last(), DEFAULT_DIR_MODE)
                    if (error != 0) {
                        throwExceptionBasedOnStatus(Native.getLastError())
                    }

                    if (owner != null) {
                        val fd = openat(finalFd, components.last(), 0, 0)
                        fchown(fd, owner, owner)
                        close(fd)
                    }
                } finally {
                    for (descriptor in fileDescriptors) {
                        if (descriptor > 0) {
                            close(descriptor)
                        }
                    }
                }
            }
        } else {
            File(file.path).mkdirs()
        }
    }

    fun move(source: InternalFile, destination: InternalFile, replaceExisting: Boolean) {
        if (Platform.isLinux()) {
            val fromParent = openFile(source.parent())
            val toParent = openFile(destination.parent())

            try {
                if (fromParent == -1 || toParent == -1) {
                    throw FSException.NotFound()
                }

                val doesToExist = if (!replaceExisting) {
                    val fd = CLibrary.INSTANCE.openat(toParent, destination.fileName(), 0, 0)
                    if (fd >= 0) {
                        CLibrary.INSTANCE.close(fd)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }

                if (doesToExist) {
                    throw FSException.AlreadyExists()
                }

                val err = CLibrary.INSTANCE.renameat(fromParent, source.fileName(), toParent, destination.fileName())
                if (err < 0) {
                    throwExceptionBasedOnStatus(Native.getLastError())
                }
            } finally {
                CLibrary.INSTANCE.close(fromParent)
                CLibrary.INSTANCE.close(toParent)
            }
        } else {
            val opts = run {
                val extraOpts: Array<CopyOption> =
                    if (replaceExisting) arrayOf(StandardCopyOption.REPLACE_EXISTING)
                    else emptyArray()

                extraOpts + arrayOf(LinkOption.NOFOLLOW_LINKS)
            }

            Files.move(File(source.path).toPath(), File(destination.path).toPath(), *opts)
        }
    }

    fun copy(
        source: InternalFile,
        destination: InternalFile,
        replaceExisting: Boolean,
        owner: Int? = LINUX_FS_USER_UID,
    ): CopyResult {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val sourceFd = openFile(source)
                val destParentFd = openFile(destination.parent())
                try {
                    if (sourceFd == -1 || destParentFd == -1) throw FSException.NotFound()
                    val sourceStat = nativeStat(sourceFd, autoClose = false)
                    val fileName = destination.fileName()
                    if (sourceStat.fileType == FileType.FILE) {
                        var opts = O_NOFOLLOW or O_TRUNC or O_CREAT or O_WRONLY
                        if (!replaceExisting) opts = opts or O_EXCL
                        val destFd = openat(destParentFd, fileName, opts, DEFAULT_FILE_MODE)
                        if (destFd < 0) {
                            close(destFd)
                            throw FSException.NotFound()
                        }

                        LinuxInputStream(sourceFd).use { ins ->
                            val outs = LinuxOutputStream(destFd)
                            ins.copyTo(outs)
                            fchmod(destFd, sourceStat.mode)
                            outs.close()
                        }
                        return CopyResult.CREATED_FILE
                    } else if (sourceStat.fileType == FileType.DIRECTORY) {
                        val destFd = openat(destParentFd, fileName, O_NOFOLLOW, 0)
                        if (destFd >= 0 || Native.getLastError() != ENOENT) {
                            val destStat = nativeStat(destFd)
                            if (destStat.fileType == FileType.DIRECTORY) return CopyResult.CREATED_DIRECTORY
                            throw FSException.IsDirectoryConflict()
                        }

                        mkdirat(destParentFd, fileName, DEFAULT_DIR_MODE)
                        if (owner != null) {
                            val fd = openat(destParentFd, fileName, O_NOFOLLOW, 0)
                            if (fd < 0) {
                                throw FSException.CriticalException("Directory disappeared after creation")
                            }

                            fchown(fd, owner, owner)
                            close(fd)
                        }

                        return CopyResult.CREATED_DIRECTORY
                    } else {
                        return CopyResult.NOTHING_TO_CREATE
                    }
                } finally {
                    close(sourceFd)
                    close(destParentFd)
                }
            }
        } else {
            val file = File(source.path)
            Files.copy(
                file.toPath(),
                File(destination.path).toPath(),
                *(if (replaceExisting) arrayOf(StandardCopyOption.REPLACE_EXISTING) else emptyArray())
            )
            return if (file.isDirectory) CopyResult.CREATED_DIRECTORY
            else CopyResult.CREATED_FILE
        }
    }

    private fun setMetadataForNewFile(
        fd: Int,
        owner: Int? = LINUX_FS_USER_UID,
        permissions: Int?,
    ) {
        require(fd >= 0)
        if (owner != null) CLibrary.INSTANCE.fchown(fd, owner, owner)
        if (permissions != null) CLibrary.INSTANCE.fchmod(fd, permissions)
    }

    fun openForWriting(
        file: InternalFile,
        allowOverwrite: Boolean,
        owner: Int? = LINUX_FS_USER_UID,
        permissions: Int?,
    ): OutputStream {
        if (Platform.isLinux()) {
            var opts = O_TRUNC or O_CREAT or O_WRONLY
            if (!allowOverwrite) {
                opts = opts or O_EXCL
            }

            val fd = openFile(file, opts)
            if (fd < 0) {
                CLibrary.INSTANCE.close(fd)
                throw FSException.NotFound()
            }

            setMetadataForNewFile(fd, owner, permissions)
            return LinuxOutputStream(fd)
        } else {
            val options = HashSet<OpenOption>()
            options.add(StandardOpenOption.TRUNCATE_EXISTING)
            options.add(StandardOpenOption.WRITE)
            options.add(LinkOption.NOFOLLOW_LINKS)
            if (!allowOverwrite) {
                options.add(StandardOpenOption.CREATE_NEW)
            } else {
                options.add(StandardOpenOption.CREATE)
            }

            try {
                val systemPath = File(file.path).toPath()
                return Channels.newOutputStream(
                    Files.newByteChannel(
                        systemPath,
                        options,
                        PosixFilePermissions.asFileAttribute(DEFAULT_POSIX_FILE_MODE)
                    )
                )
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

    fun listFiles(file: InternalFile): List<String> {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val fd = openFile(file)
                if (fd < 0) {
                    close(fd)
                    throw FSException.NotFound()
                }

                val dir = fdopendir(fd)
                if (dir == null) {
                    close(fd)
                    throw FSException.IsDirectoryConflict()
                }

                val result = ArrayList<String>()
                while (true) {
                    val ent = readdir(dir) ?: break
                    // Read unsized string at end of struct. The ABI for this function leaves the size completely
                    // undefined.
                    val name = ent.pointer.getString(19)
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

    fun delete(file: InternalFile) {
        if (Platform.isLinux()) {
            val fd = openFile(file.parent())
            if (fd < 0) throw FSException.NotFound()
            try {
                if (CLibrary.INSTANCE.unlinkat(fd, file.fileName(), AT_REMOVEDIR) < 0) {
                    if (CLibrary.INSTANCE.unlinkat(fd, file.fileName(), 0) < 0) {
                        if (Native.getLastError() == ENOTEMPTY) {
                            throw FSException.BadRequest()
                        }

                        throw FSException.NotFound()
                    }
                }
            } finally {
                CLibrary.INSTANCE.close(fd)
            }
        } else {
            Files.delete(File(file.path).toPath())
        }
    }

    fun readNativeFilePermissons(file: InternalFile): Int {
        return if (Platform.isLinux()) {
            val fd = openFile(file)
            if (fd < 0) throw FSException.NotFound()
            val st = stat()
            st.write()
            val err = CLibrary.INSTANCE.__fxstat64(1, fd, st.pointer)
            st.read()
            CLibrary.INSTANCE.close(fd)
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
            if (fd < 0) throw FSException.NotFound()
            return nativeStat(fd)
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
            return NativeStat(size, modifiedAt, fileType, LINUX_FS_USER_UID, DEFAULT_FILE_MODE)
        }
    }

    private fun nativeStat(fd: Int, autoClose: Boolean = true): NativeStat {
        require(fd >= 0)
        val st = stat()
        st.write()
        val err = CLibrary.INSTANCE.__fxstat64(1, fd, st.pointer)
        st.read()

        if (autoClose) CLibrary.INSTANCE.close(fd)
        if (err < 0) {
            throw FSException.NotFound()
        }

        return NativeStat(
            st.st_size,
            (st.m_sec * 1000) + (st.m_nsec / 1_000_000),
            if (st.st_mode and S_ISREG == 0) FileType.DIRECTORY else FileType.FILE,
            st.st_uid,
            st.st_mode
        )
    }

    fun chmod(file: InternalFile, mode: Int) {
        if (!Platform.isLinux()) return
        val fd = openFile(file)
        try {
            CLibrary.INSTANCE.fchmod(fd, mode)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
    }

    fun chown(file: InternalFile, uid: Int, gid: Int) {
        if (!Platform.isLinux() || disableChown) return
        val fd = openFile(file)
        try {
            CLibrary.INSTANCE.fchown(fd, uid, gid)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
    }

    fun changeFilePermissions(file: InternalFile, mode: Int, uid: Int, gid: Int) {
        if (!Platform.isLinux()) return
        val fd = openFile(file)
        try {
            CLibrary.INSTANCE.fchmod(fd, mode)
            CLibrary.INSTANCE.fchown(fd, uid, gid)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
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