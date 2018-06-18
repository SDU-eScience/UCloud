package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.cephfs.*
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files

fun simpleCloudToCephFSDao(): CloudToCephFsDao {
    val dao = mockk<CloudToCephFsDao>()
    every { dao.findUnixUser(any()) } answers {
        firstArg() as String
    }

    every { dao.findCloudUser(any()) } answers {
        firstArg() as String
    }
    return dao
}

fun cephFSWithRelaxedMocks(
    fsRoot: String,
    cloudToCephFsDao: CloudToCephFsDao = simpleCloudToCephFSDao(),
    processRunner: ProcessRunnerFactory = SimpleCephFSProcessRunnerFactory(cloudToCephFsDao, true),
    fileACLService: FileACLService = mockk(relaxed = true),
    copyService: CopyService = mockk(relaxed = true),
    isDevelopment: Boolean = true,
    eventProducer: StorageEventProducer = mockk(relaxed = true)
): CephFSFileSystemService {
    return CephFSFileSystemService(
        cloudToCephFsDao,
        processRunner,
        fileACLService,
        copyService,
        fsRoot,
        isDevelopment,
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

