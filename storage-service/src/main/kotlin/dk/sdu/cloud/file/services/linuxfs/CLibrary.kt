package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.PointerType

interface CLibrary : Library {
    fun getuid(): Int
    fun setgid(gid: Int): Int
    fun setuid(uid: Int): Int
    fun setfsuid(uid: Long): Int
    fun setfsgid(uid: Long): Int
    fun realpath(path: String, destination: ByteArray?): String?
    fun getxattr(path: String, name: String, value: ByteArray, size: Int): Int
    fun setxattr(path: String, name: String, value: ByteArray, size: Int, position: Int): Int
    fun listxattr(path: String, destination: ByteArray, length: Int): Int
    fun removexattr(path: String, name: String): Int
    fun umask(value: Int): Int
    fun chown(path: String, owner: Int, group: Int): Int

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

