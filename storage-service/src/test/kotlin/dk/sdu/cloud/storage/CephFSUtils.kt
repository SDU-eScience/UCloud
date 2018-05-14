package dk.sdu.cloud.storage

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
    xAttrService: XAttrService = mockk(relaxed = true),
    treeService: TreeService = mockk(relaxed = true),
    copyService: CopyService = mockk(relaxed = true),
    removeService: RemoveService = mockk(relaxed = true),
    isDevelopment: Boolean = true,
    eventProducer: StorageEventProducer = mockk(relaxed = true)
): CephFSFileSystemService {
    return CephFSFileSystemService(
        cloudToCephFsDao,
        processRunner,
        fileACLService,
        xAttrService,
        treeService,
        copyService,
        removeService,
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

