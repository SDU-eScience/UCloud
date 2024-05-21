package libc

import dk.sdu.cloud.utils.sendTerminalMessage
import io.ktor.util.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.*
import kotlin.system.exitProcess

class NativeStat(
    val valid: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val ownerUid: Int,
    val ownerGid: Int,
    val mode: Int,
)

class LibC {
    external fun open(path: String?, flags: Int, mode: Int): Int
    external fun openat(fd: Int, path: String?, flags: Int, mode: Int): Int
    external fun close(fd: Int): Int
    external fun renameat(oldFd: Int, oldName: String?, newFd: Int, newName: String?): Int
    external fun write(fd: Int, buffer: ByteBuffer, offset: Int, size: Int): Int
    external fun read(fd: Int, buffer: ByteBuffer, offset: Int, size: Int): Int
    external fun lseek(fd: Int, offset: Long, whence: Int): Long
    external fun unlinkat(fd: Int, path: String?, flags: Int): Int
    external fun fchown(fd: Int, uid: Int, gid: Int): Int
    external fun fchmod(fd: Int, mode: Int): Int
    external fun fgetxattr(fd: Int, name: String?, value: ByteBuffer): Int
    external fun fsetxattr(fd: Int, name: String?, value: ByteArray?, size: Int, flags: Int): Int
    external fun fremovexattr(fd: Int, name: String?): Int
    external fun fdopendir(fd: Int): Long
    external fun readdir(pointer: Long): Dirent?

    external fun socket(domain: Int, type: Int, protocol: Int): Int
    external fun connect(sockFd: Int, address: Long, addressLength: Int): Int
    external fun unixDomainSocketSize(): Int
    external fun buildUnixSocketAddress(path: String): Long
    external fun receiveMessage(sockFd: Int, buffer: ByteBuffer, uidAndGid: IntArray? = null): Int
    external fun sendMessage(sockFd: Int, buffer: ByteBuffer): Int

    external fun bind(sockFd: Int, address: Long, addressLength: Int): Int
    external fun listen(sockFd: Int, backlog: Int): Int
    external fun accept(sockFd: Int): Int
    external fun chmod(path: String, mode: Int): Int

    external fun retrieveUserIdFromName(name: String): Int
    external fun retrieveGroupIdFromName(name: String): Int

    external fun getErrno(): Int
    external fun mkdirat(dirfd: Int, pathName: String, mode: Int): Int
    external fun closedir(dirp: Long): Int
    external fun fstat(fd: Int): NativeStat

    external fun getuid(): Int

    external fun createAndForkPty(command: Array<String>, env: Array<String>): Int
    external fun resizePty(masterFd: Int, cols: Int, rows: Int): Int
    external fun umask(mask: Int): Int

    external fun touchFile(fileDescriptor: Int): Int
    external fun modifyTimestamps(fileDescriptor: Int, modifiedAt: Long): Int

    external fun scanFiles(paths: Array<String>, sizes: LongArray, modifiedTimestamps: LongArray, result: BooleanArray): Int

    companion object {
        init {
            var didLoad = false

            val locationsForExtraction = listOfNotNull(
                Files.createTempDirectory("ucloud-lib").toFile().also { it.deleteOnExit() },
                System.getenv("UCLOUD_CODE_DIR").takeIf { it.isNullOrBlank() }?.let { File(it) },
                File("/tmp/")
            )

            for (file in locationsForExtraction) {
                if (!file.exists()) continue

                try {
                    val data = if (System.getProperty("os.arch") == "aarch64") libcSharedDataArm64 else libcSharedData
                    val randomIdentifier = UUID.randomUUID().toString()
                    val outputFile = File(file, "libc_wrapper_$randomIdentifier.so")
                    outputFile.writeBytes(Base64.getDecoder().decode(data.replace("\n", "")))
                    Files.setPosixFilePermissions(
                        outputFile.toPath(),
                        PosixFilePermissions.fromString("r-x------")
                    )
                    try {
                        System.load(outputFile.absolutePath)
                    } catch (ex: Throwable) {
                        ex.printStackTrace()
                        runCatching { outputFile.delete() }
                        continue
                    }
                    outputFile.deleteOnExit()
                    didLoad = true
                    break
                } catch (ex: Throwable) {
                    continue
                }
            }

            val potentialLocations = listOf(
                File("./libc_wrapper.so"),
                File("./native/libc_wrapper.so"),
                File("/opt/ucloud/native/libc_wrapper.so")
            )

            if (!didLoad) {
                for (file in potentialLocations) {
                    if (!file.exists()) continue
                    runCatching { System.load(file.absolutePath) }.getOrNull() ?: continue
                    didLoad = true
                    break
                }
            }

            if (!didLoad) {
                sendTerminalMessage {
                    bold { red { line("Could not load native library! (os.arch = ${System.getProperty("os.arch")})") } }
                    line()
                    line("The native support library must be loaded but it wasn't found in any of the expected locations.")
                    line()

                    inline("You can build the native library by running ")
                    code { inline("./build.sh") }
                    inline(" from the ")
                    code { inline("./native") }
                    inline(" directory of the integration module's source.")
                    line()

                    line()
                    line("We tried to locate the library in the following locations:")
                    for (loc in potentialLocations) {
                        inline(" - ")
                        code { line(loc.absolutePath) }
                    }

                    line()
                    bold { line("Remember to ensure that the service user has the appropriate permissions for loading this library!") }
                }
                exitProcess(1)
            }
        }
    }
}

class Dirent {
    var d_ino: Long = 0L
    var d_off: Long = 0L
    var d_reclen: Short = 0
    var d_type: Byte = 0
    var d_name: ByteBuffer = ByteBuffer.allocateDirect(0)
    override fun toString(): String {
        return "Dirent(d_ino=$d_ino, d_off=$d_off, d_reclen=$d_reclen, d_type=$d_type, d_name=${d_name.decodeString()})"
    }
}

val clib = LibC()
