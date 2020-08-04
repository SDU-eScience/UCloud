package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import dk.sdu.cloud.file.api.*
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.throwExceptionBasedOnStatus
import dk.sdu.cloud.service.Loggable
import kotlinx.io.pool.useInstance
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

data class NativeStat(
    val size: Long,
    val modifiedAt: Long,
    val fileType: FileType,
    val ownSensitivityLevel: SensitivityLevel?,
    val ownerUid: Int
)

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

    private fun openFile(path: String, flag: Int = 0): Int {
        with(CLibrary.INSTANCE) {
            val components = path.components()
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

    fun createDirectories(path: File, owner: Int? = LINUX_FS_USER_UID) {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val components = path.absolutePath.components()
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
            path.mkdirs()
        }
    }

    fun move(from: File, to: File, replaceExisting: Boolean) {
        if (Platform.isLinux()) {
            val fromParent = openFile(from.parentFile.absolutePath)
            val toParent = openFile(to.parentFile.absolutePath)

            try {
                if (fromParent == -1 || toParent == -1) {
                    throw FSException.NotFound()
                }

                val doesToExist = if (!replaceExisting) {
                    val fd = CLibrary.INSTANCE.openat(toParent, to.name, 0, 0)
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

                val err = CLibrary.INSTANCE.renameat(fromParent, from.name, toParent, to.name)
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

            Files.move(from.toPath(), to.toPath(), *opts)
        }
    }

    fun openForWriting(path: File, allowOverwrite: Boolean, owner: Int? = LINUX_FS_USER_UID): OutputStream {
        if (Platform.isLinux()) {
            val exists = if (owner != null) runCatching { stat(path) }.isSuccess else null

            var opts = O_TRUNC or O_CREAT or O_WRONLY
            if (!allowOverwrite) {
                opts = opts or O_EXCL
            }

            val fd = openFile(path.absolutePath, opts)
            if (fd < 0) {
                CLibrary.INSTANCE.close(fd)
                throw FSException.NotFound()
            }

            if (owner != null && exists == false) {
                CLibrary.INSTANCE.fchown(fd, owner, owner)
            }

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
                val systemPath = path.toPath()
                return Channels.newOutputStream(
                    Files.newByteChannel(
                        systemPath,
                        options,
                        PosixFilePermissions.asFileAttribute(LinuxFS.DEFAULT_FILE_MODE)
                    )
                )
            } catch (ex: FileAlreadyExistsException) {
                throw FSException.AlreadyExists()
            } catch (ex: java.nio.file.FileSystemException) {
                if (ex.message?.contains("Is a directory") == true) {
                    throw FSException.BadRequest("Upload target is a not a directory")
                } else {
                    throw ex
                }
            }
        }
    }

    fun openForReading(path: File): InputStream {
        return if (Platform.isLinux()) {
            LinuxInputStream(openFile(path.absolutePath, O_RDONLY)).buffered()
        } else {
            FileInputStream(path)
        }
    }

    fun listFiles(path: File): List<String> {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val fd = openFile(path.absolutePath)
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
            return path.list()?.toList() ?: emptyList()
        }
    }

    fun delete(path: File) {
        if (Platform.isLinux()) {
            val fd = openFile(path.parentFile.absolutePath)
            if (fd < 0) throw FSException.NotFound()
            try {
                if (CLibrary.INSTANCE.unlinkat(fd, path.name, AT_REMOVEDIR) < 0) {
                    if (CLibrary.INSTANCE.unlinkat(fd, path.name, 0) < 0) {
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
            Files.delete(path.toPath())
        }
    }

    fun getExtendedAttribute(path: File, attribute: String): String {
        return if (Platform.isLinux()) {
            val fd = openFile(path.absolutePath)
            try {
                getExtendedAttribute(fd, attribute)
            } finally {
                CLibrary.INSTANCE.close(fd)
            }
        } else {
            DefaultByteArrayPool.useInstance {
                val read = XAttrOSX.INSTANCE.getxattr(path.absolutePath, attribute, it, it.size, 0, 0)
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

    fun stat(path: File): NativeStat {
        if (Platform.isLinux()) {
            val fd = openFile(path.absolutePath)
            if (fd < 0) throw FSException.NotFound()
            val st = stat()
            st.write()
            val err = CLibrary.INSTANCE.__fxstat64(1, fd, st.pointer)
            st.read()

            val ownSensitivityLevel = run {
                val attr = runCatching { getExtendedAttribute(fd, "user.sensitivity") }.getOrNull()
                if (attr != null) runCatching { SensitivityLevel.valueOf(attr) }.getOrNull()
                else null
            }

            CLibrary.INSTANCE.close(fd)
            if (err < 0) {
                throw FSException.NotFound()
            }

            return NativeStat(
                st.st_size,
                (st.m_sec * 1000) + (st.m_nsec / 1_000_000),
                if (st.st_mode and S_ISREG == 0) FileType.DIRECTORY else FileType.FILE,
                ownSensitivityLevel,
                st.st_uid
            )
        } else {
            if (Files.isSymbolicLink(path.toPath())) throw FSException.NotFound()
            val ownSensitivityLevel = run {
                val attr = runCatching { getExtendedAttribute(path, "user.sensitivity") }.getOrNull()
                if (attr != null) runCatching { SensitivityLevel.valueOf(attr) }.getOrNull()
                else null
            }

            val basicAttributes = run {
                val opts = listOf("size", "lastModifiedTime", "isDirectory")
                Files.readAttributes(path.toPath(), opts.joinToString(","), LinkOption.NOFOLLOW_LINKS)
            }

            val size = basicAttributes.getValue("size") as Long
            val fileType = if (basicAttributes.getValue("isDirectory") as Boolean) {
                FileType.DIRECTORY
            } else {
                FileType.FILE
            }

            val modifiedAt = (basicAttributes.getValue("lastModifiedTime") as FileTime).toMillis()
            return NativeStat(size, modifiedAt, fileType, ownSensitivityLevel, LINUX_FS_USER_UID)
        }
    }

    fun chmod(path: File, mode: Int) {
        if (!Platform.isLinux()) return
        val fd = openFile(path.absolutePath)
        try {
            CLibrary.INSTANCE.fchmod(fd, mode)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
    }

    fun chown(path: File, uid: Int, gid: Int) {
        if (!Platform.isLinux() || disableChown) return
        val fd = openFile(path.absolutePath)
        try {
            CLibrary.INSTANCE.fchown(fd, uid, gid)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
    }

    fun changeFilePermissions(path: File, mode: Int, uid: Int, gid: Int) {
        if (!Platform.isLinux()) return
        val fd = openFile(path.absolutePath)
        try {
            CLibrary.INSTANCE.fchmod(fd, mode)
            CLibrary.INSTANCE.fchown(fd, uid, gid)
        } finally {
            CLibrary.INSTANCE.close(fd)
        }
    }
}

class NativeException(val statusCode: Int) : RuntimeException("Native exception, code: $statusCode")
