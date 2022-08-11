package libc

import dk.sdu.cloud.utils.sendTerminalMessage
import io.ktor.util.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.system.exitProcess

class LibC {
    external fun open(path: String?, flags: Int, mode: Int): Int
    external fun openat(fd: Int, path: String?, flags: Int, mode: Int): Int
    external fun close(fd: Int): Int
    external fun renameat(oldFd: Int, oldName: String?, newFd: Int, newName: String?): Int
    external fun write(fd: Int, buffer: ByteArray?, bufferSize: Long): Int
    external fun read(fd: Int, buffer: ByteArray?, bufferSize: Long): Int
    external fun lseek(fd: Int, offset: Long, whence: Int): Long
    external fun unlinkat(fd: Int, path: String?, flags: Int): Int
    external fun fchown(fd: Int, uid: Int, gid: Int): Int
    external fun fchmod(fd: Int, mode: Int): Int
    external fun fgetxattr(fd: Int, name: String?, value: ByteArray?, size: Int): Int
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

    external fun getuid(): Int

    companion object {
        init {
            val potentialLocations = listOf(File("./libc_wrapper.so"), File("./native/libc_wrapper.so"), File("/opt/ucloud/native/libc_wrapper.so"))
            var didLoad = false
            for (file in potentialLocations) {
                if (!file.exists()) continue
                System.load(file.absolutePath)
                didLoad = true
                break
            }

            if (!didLoad) {
                sendTerminalMessage {
                    bold { red { line("Could not load native library!") } }
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
                    line("Remember to ensure that the service user has the appropiate permissions for loading this library!")
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
