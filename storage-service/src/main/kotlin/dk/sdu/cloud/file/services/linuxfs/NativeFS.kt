package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Native
import com.sun.jna.Platform
import dk.sdu.cloud.file.api.components
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.Loggable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermissions

object NativeFS : Loggable {
    private const val O_NOFOLLOW = 0x20000
    private const val O_TRUNC = 0x200
    private const val O_CREAT = 0x40
    private const val O_EXCL = 0x80
    private const val O_WRONLY = 0x1
    private const val O_RDONLY = 0x0
    private const val ENOENT = 2
    private const val DEFAULT_MODE = 504 // 0770
    override val log = logger()

    private fun openFile(path: String, flag: Int = 0): IntArray {
        with(CLibrary.INSTANCE) {
            val components = path.components()
            val fileDescriptors = IntArray(components.size) { -1 }
            try {
                fileDescriptors[0] = open("/${components[0]}", O_NOFOLLOW, DEFAULT_MODE)
                for (i in 1 until fileDescriptors.size) {
                    val previousFd = fileDescriptors[i - 1]
                    if (previousFd < 0) {
                        throw FSException.NotFound()
                    }

                    val opts =
                        if (i == fileDescriptors.lastIndex) O_NOFOLLOW or flag
                        else O_NOFOLLOW
                    fileDescriptors[i] = openat(fileDescriptors[i - 1], components[i], opts, DEFAULT_MODE)
                }
            } catch (ex: Throwable) {
                fileDescriptors.closeAll()
                throw ex
            }

            return fileDescriptors
        }
    }

    private fun IntArray.closeAll() {
        for (descriptor in this) {
            if (descriptor > 0) {
                CLibrary.INSTANCE.close(descriptor)
            }
        }
    }

    fun createDirectories(path: File) {
        if (Platform.isLinux()) {
            with(CLibrary.INSTANCE) {
                val components = path.absolutePath.components()
                if (components.isEmpty()) throw IllegalArgumentException("Path is empty")

                val fileDescriptors = IntArray(components.size - 1) { -1 }
                try {
                    fileDescriptors[0] = open("/${components[0]}", O_NOFOLLOW, DEFAULT_MODE)
                    var i = 1
                    while (i < fileDescriptors.size) {
                        val previousFd = fileDescriptors[i - 1]
                        if (previousFd < 0) {
                            throw FSException.NotFound()
                        }

                        fileDescriptors[i] = openat(fileDescriptors[i - 1], components[i], O_NOFOLLOW, DEFAULT_MODE)

                        if (fileDescriptors[i] < 0 && Native.getLastError() == ENOENT) {
                            val err = mkdirat(fileDescriptors[i - 1], components[i], DEFAULT_MODE)
                            if (err < 0) throw FSException.NotFound()
                        } else {
                            i++
                        }
                    }

                    val finalFd = fileDescriptors.last()
                    if (finalFd < 0) throw FSException.NotFound()

                    val error = mkdirat(finalFd, components.last(), DEFAULT_MODE)
                    if (error != 0) {
                        throw FSException.NotFound()
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
            path.mkdir()
        }
    }

    fun move(from: File, to: File, replaceExisting: Boolean) {
        if (Platform.isLinux()) {
            val fromFds = openFile(from.parentFile.absolutePath)
            val toFds = openFile(to.parentFile.absolutePath)

            try {
                val fromParent = fromFds.last()
                val toParent = toFds.last()

                if (fromParent == -1 || toParent == -1) {
                    throw FSException.NotFound()
                }

                val doesToExist = if (!replaceExisting) {
                    val fd = CLibrary.INSTANCE.openat(toParent, to.name, 0, DEFAULT_MODE)
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
                    throw FSException.NotFound()
                }
            } finally {
                fromFds.closeAll()
                toFds.closeAll()
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

    fun openForWriting(path: File, allowOverwrite: Boolean): OutputStream {
        if (Platform.isLinux()) {
            var opts = O_TRUNC or O_CREAT or O_WRONLY
            if (!allowOverwrite) {
                opts = opts or O_EXCL
            }

            val fd = openFile(path.absolutePath, opts)
            if (fd.any { it < 0 }) {
                fd.closeAll()
                throw FSException.NotFound()
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
}
