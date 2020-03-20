package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.*

@Suppress("SpellCheckingInspection", "FunctionName")
interface CLibrary : Library {
    fun fgetxattr(fd: Int, name: String, value: ByteArray, size: Int): Int
    fun fsetxattr(fd: Int, name: String, value: ByteArray, size: Int, flags: Int): Int
    fun fremovexattr(fd: Int, name: String): Int

    fun mkdirat(dirfd: Int, pathName: String, mode: Int): Int
    fun open(path: String, oflag: Int, mode: Int): Int
    fun openat(fd: Int, path: String, oflag: Int, mode: Int): Int
    fun close(fd: Int): Int
    fun renameat(oldFd: Int, oldName: String, newFd: Int, newName: String): Int
    fun write(fd: Int, buffer: ByteArray, bufferSize: Long): Long
    fun read(fd: Int, buffer: ByteArray, size: Long): Long
    fun lseek(fd: Int, offset: Long, whence: Int): Long
    fun readdir(dirp: Pointer?): dirent?
    fun fdopendir(fd: Int): Pointer?
    fun closedir(dirp: Pointer?): Int
    fun unlinkat(fd: Int, path: String, flag: Int): Int
    fun fchown(fd: Int, uid: Int, gid: Int): Int
    fun fchmod(fd: Int, mode: Int): Int

    // The function in the stat family are linked statically. As a result we must use the __xstat family, more info:
    // https://refspecs.linuxfoundation.org/LSB_1.1.0/gLSB/baselib-xstat-1.html
    // https://stackoverflow.com/questions/45634018/why-are-stat-fstat-linked-statically
    // https://stackoverflow.com/questions/44294173/moving-to-different-linux-build-system-getting-error-undefined-symbol-stat/44320282#44320282
    //
    // The version must be 1 despite what the link above states
    fun __fxstat64(version: Int, fd: Int, statBuf: Pointer): Int

    companion object {
        val INSTANCE =
            Native.load(
                if (Platform.isWindows()) "msvcrt" else "c",
                CLibrary::class.java
            ) as CLibrary
    }
}

@Suppress("SpellCheckingInspection")
interface XAttrOSX : Library {
    fun getxattr(path: String, name: String, value: ByteArray, size: Int, position: Int, options: Int): Int
    fun setxattr(path: String, name: String, value: ByteArray, size: Int, position: Int, options: Int): Int
    fun removexattr(path: String, name: String, options: Int): Int

    companion object {
        val INSTANCE = Native.load("c", XAttrOSX::class.java) as XAttrOSX
    }
}
