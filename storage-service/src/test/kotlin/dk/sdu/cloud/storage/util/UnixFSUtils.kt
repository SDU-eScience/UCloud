package dk.sdu.cloud.storage.util

import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.file.services.StorageUserDao
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.unixfs.UnixFileSystem
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun simpleStorageUserDao(): StorageUserDao {
    val dao = mockk<StorageUserDao>()
    every { dao.findStorageUser(any()) } answers {
        firstArg() as String
    }

    every { dao.findCloudUser(any()) } answers {
        firstArg() as String
    }
    return dao
}

fun storageUserDaoWithFixedAnswer(answer: String): StorageUserDao {
    val dao = mockk<StorageUserDao>()
    every { dao.findStorageUser(any()) } returns answer
    every { dao.findCloudUser(any()) } returns answer
    return dao
}

fun unixFSWithRelaxedMocks(
    fsRoot: String,
    userDao: StorageUserDao = simpleStorageUserDao()
): Pair<UnixFSCommandRunnerFactory, UnixFileSystem> {
    val commandRunner = UnixFSCommandRunnerFactory(userDao, true)
    return Pair(
        commandRunner,
        UnixFileSystem(
            userDao,
            fsRoot
        )
    )
}

@UseExperimental(ExperimentalContracts::class)
inline fun File.mkdir(name: String, closure: File.() -> Unit): File {
    contract {
        callsInPlace(closure, InvocationKind.EXACTLY_ONCE)
    }

    val f = File(this, name)
    f.mkdir()
    f.closure()
    return f
}

fun File.touch(name: String, contents: String = "Hello!"): File {
    return File(this, name).also { it.writeText(contents) }
}

fun File.inode(): String {
    return Files
        .readAttributes(toPath(), BasicFileAttributes::class.java)
        .fileKey()
        .toString()
        .substringAfter("ino=")
        .removeSuffix(")")
}

fun File.timestamps(): Timestamps {
    val attrs = Files.readAttributes(toPath(), BasicFileAttributes::class.java)
    return Timestamps(
        accessed = attrs.lastAccessTime().toMillis(),
        created = attrs.creationTime().toMillis(),
        modified = attrs.lastModifiedTime().toMillis()
    )
}

fun createDummyFS(): File {
    val fsRoot = Files.createTempDirectory("share-service-test").toFile()
    fsRoot.apply { createDummyFSInRoot() }
    return fsRoot
}

fun File.createDummyFSInRoot() {
    mkdir("home") {
        mkdir("user1") {
            mkdir("folder") {
                touch("a", "File A")
                touch("b", "File B")
                touch("c", "File C")
                touch("d", "File E")
                touch("e", "File F")

            }

            mkdir("another-one") {
                touch("b")
                touch("g", "File G")
                touch("h", "File H")

            }
            mkdir("one") {
                touch("a", "File AA")
                touch("i", "File I")
                touch("j", "File J")
                touch("file", "File BB")
            }
            mkdir("Favorites") {}
        }
    }
}

fun createFS(builder: File.() -> Unit): String {
    return Files.createTempDirectory("storage-service-test").toFile().apply { builder() }.absolutePath
}
