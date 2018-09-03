package dk.sdu.cloud.storage.util

import dk.sdu.cloud.file.api.Timestamps
import dk.sdu.cloud.storage.services.StorageUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

fun simpleCloudToCephFSDao(): StorageUserDao {
    val dao = mockk<StorageUserDao>()
    every { dao.findStorageUser(any()) } answers {
        firstArg() as String
    }

    every { dao.findCloudUser(any()) } answers {
        firstArg() as String
    }
    return dao
}

fun cloudToCephFsDAOWithFixedAnswer(answer: String): StorageUserDao {
    val dao = mockk<StorageUserDao>()
    every { dao.findStorageUser(any()) } returns answer
    every { dao.findCloudUser(any()) } returns answer
    return dao
}

fun cephFSWithRelaxedMocks(
    fsRoot: String,
    userDao: StorageUserDao = simpleCloudToCephFSDao()
): Pair<CephFSCommandRunnerFactory, CephFileSystem> {
    val commandRunner = CephFSCommandRunnerFactory(userDao, true)
    return Pair(
        commandRunner,
        CephFileSystem(
            userDao,
            fsRoot
        )
    )
}

fun File.mkdir(name: String, closure: File.() -> Unit): File {
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
    fsRoot.apply {
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
    return fsRoot
}

fun createFS(builder: File.() -> Unit): String {
    return Files.createTempDirectory("storage-service-test").toFile().apply { builder() }.absolutePath
}
