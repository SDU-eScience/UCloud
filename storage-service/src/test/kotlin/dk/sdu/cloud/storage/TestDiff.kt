package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.IndexingService
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import io.mockk.mockk
import java.io.File

/*fun main(args: Array<String>) {
    val userDao = CephFSUserDao(true)
    val cephFs = CephFileSystem(userDao, File("fs").absolutePath)
    val eventProducer = mockk<StorageEventProducer>(relaxed = true)
    val coreFs = CoreFileSystemService(cephFs, eventProducer)
    val indexingService = IndexingService(coreFs, eventProducer)

    val commandRunnerFactory = CephFSCommandRunnerFactory(userDao, true)
    val diff = commandRunnerFactory.withContext("jonas@hinchely.dk") { ctx ->
        indexingService.calculateDiff(
            ctx, "/home/jonas@hinchely.dk", emptyList()
        )
    }

    println("Should continue: ${diff.shouldContinue}")
    diff.diff.forEach { println(it) }
}*/