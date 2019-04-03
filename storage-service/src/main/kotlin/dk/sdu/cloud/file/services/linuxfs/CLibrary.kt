package dk.sdu.cloud.file.services.linuxfs

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer

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


// This is an insane API
interface ACLLibrary : Library {
    fun acl_get_file(path: String, type: Int): Pointer?
    fun acl_get_entry(tag: Pointer?, entryIdx: Int, destination: ACLEntryBuf?): Int
    fun acl_get_tag_type(entry: ACLEntry, tag: ACLTag?): Int
    fun acl_get_qualifier(entry: ACLEntry): Pointer?
    fun acl_get_permset(entry: ACLEntry, destination: ACLPermSet?): Int
    fun acl_get_perm(permset: Long, value: Int): Int
    fun acl_free(pointer: Pointer)

    companion object {
        val INSTANCE by lazy {
            if (Platform.isLinux()) Native.load("acl", ACLLibrary::class.java) as ACLLibrary
            else Native.load("c", ACLLibrary::class.java) as ACLLibrary
        }
    }
}

// The following types are seemingly meant to be anonymous. They can only be queried with the functions in the API.
// For this reason whenever we need to use them we just use a buffer of the appropriate size. These constructor
// functions should take into account differences between OSX and Linux.

typealias ACLEntryBuf = LongArray
typealias ACLEntry = Long

typealias ACLTag = IntArray

fun ACLTag() = IntArray(1)

typealias ACLPermSet = LongArray

fun ACLPermSet() = LongArray(1)
