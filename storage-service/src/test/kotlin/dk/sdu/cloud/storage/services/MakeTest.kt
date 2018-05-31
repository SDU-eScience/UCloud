package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEventProducer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MakeTest {

    @Test (expected = FileSystemException.AlreadyExists::class)
    fun testNewDirAlreadyExists() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            eventProducer = emitter
        )

        val existingFolder = File(fsRoot, "home/user1/folder")
        Assert.assertTrue(existingFolder.exists())

        fs.mkdir(fs.openContext("user1"), "/home/user1/folder")

    }

    @Test
    fun testPathSanitation() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            eventProducer = emitter
        )

        val existingFolder = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertFalse(existingFolder.exists())

        fs.mkdir(fs.openContext("user1"), "/home/user1/folder/./../folder/./Weirdpath")

        val existingFolder2 = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertTrue(existingFolder2.exists())
    }


}