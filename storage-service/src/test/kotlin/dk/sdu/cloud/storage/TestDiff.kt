package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.FileType
import dk.sdu.cloud.storage.services.CoreFileSystemService
import dk.sdu.cloud.storage.services.IndexingService
import dk.sdu.cloud.storage.services.cephfs.CephFSCommandRunnerFactory
import dk.sdu.cloud.storage.services.cephfs.CephFSUserDao
import dk.sdu.cloud.storage.services.cephfs.CephFileSystem
import io.mockk.mockk
import java.io.File

fun main(args: Array<String>) {
    val userDao = CephFSUserDao(true)
    val cephFs = CephFileSystem(userDao, File("fs").absolutePath)
    val coreFs = CoreFileSystemService(cephFs, mockk(relaxed = true))
    val indexingService = IndexingService(coreFs)

    val commandRunnerFactory = CephFSCommandRunnerFactory(userDao, true)
    val diff = commandRunnerFactory.withContext("jonas@hinchely.dk") { ctx ->
        indexingService.calculateDiff(
            ctx, "/home/jonas@hinchely.dk", listOf(
                IndexingService.SlimStorageFile(
                    "8595440722",
                    "/home/jonas@hinchely.dk/Favorites",
                    "some other user",
                    FileType.DIRECTORY
                )
            )
        )
    }

    println("Should continue: ${diff.shouldContinue}")
    diff.diff.forEach { println(it) }
}