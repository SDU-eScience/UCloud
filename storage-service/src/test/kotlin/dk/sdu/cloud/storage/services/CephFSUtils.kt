package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

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

fun File.mkdir(name: String, closure: File.() -> Unit) {
    val f = File(this, name)
    f.mkdir()
    f.closure()
}

fun File.touch(name: String, contents: String = "Hello!") {
    File(this, name).writeText(contents)
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
                }

                mkdir("another-one") {
                    touch("file")
                }
                mkdir("Favorites") {}
            }
        }
    }
    return fsRoot
}

