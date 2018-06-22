package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.cephfs.*
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

fun simpleCloudToCephFSDao(): CephFSUserDao {
    val dao = mockk<CephFSUserDao>()
    every { dao.findUnixUser(any()) } answers {
        firstArg() as String
    }

    every { dao.findCloudUser(any()) } answers {
        firstArg() as String
    }
    return dao
}

fun cloudToCephFsDAOWithFixedAnswer(answer: String): CephFSUserDao {
    val dao = mockk<CephFSUserDao>()
    every { dao.findUnixUser(any()) } returns answer
    every { dao.findCloudUser(any()) } returns answer
    return dao
}

fun cephFSWithRelaxedMocks(
    fsRoot: String,
    cephFSUserDao: CephFSUserDao = simpleCloudToCephFSDao(),
    processRunner: ProcessRunnerFactory = CephFSProcessRunnerFactory(
        cephFSUserDao,
        true
    ),
    fileACLService: FileACLService = mockk(relaxed = true),
    eventProducer: StorageEventProducer = mockk(relaxed = true)
): CephFSFileSystemService {
    return CephFSFileSystemService(
        cephFSUserDao,
        processRunner,
        fileACLService,
        fsRoot,
        eventProducer
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
                mkdir("Favorites"){}
            }
        }
    }
    return fsRoot
}

