package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.*

val dirent.name: String
    get() {
        val size = d_name.indexOf(0)
        if (size == -1) throw IllegalStateException("End of file name not found!")
        return String(d_name, 0, size, Charsets.UTF_8)
    }

interface CLibrary : Library {
    fun realpath(path: String, destination: ByteArray?): String?
    fun getxattr(path: String, name: String, value: ByteArray, size: Int): Int
    fun setxattr(path: String, name: String, value: ByteArray, size: Int, position: Int): Int
    fun listxattr(path: String, destination: ByteArray, length: Int): Int
    fun removexattr(path: String, name: String): Int
    fun setfsuid(uid: Long): Int
    fun setfsgid(uid: Long): Int
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

    companion object {
        val INSTANCE =
            Native.load(
                if (Platform.isWindows()) "msvcrt" else "c",
                CLibrary::class.java
            ) as CLibrary
    }
}

interface XAttrOSX : Library {
    fun getxattr(path: String, name: String, value: ByteArray, size: Int, position: Int, options: Int): Int
    fun setxattr(path: String, name: String, value: ByteArray, size: Int, position: Int, options: Int): Int
    fun listxattr(path: String, destination: ByteArray, length: Int, options: Int): Int
    fun removexattr(path: String, name: String, options: Int): Int

    companion object {
        val INSTANCE = Native.load("c", XAttrOSX::class.java) as XAttrOSX
    }
}
