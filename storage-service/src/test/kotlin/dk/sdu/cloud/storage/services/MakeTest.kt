package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEventProducer
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File

class MakeTest {

    @Test (expected = FSException.AlreadyExists::class)
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

        fs.withContext("user1") {
            fs.mkdir(it, "/home/user1/folder")
        }
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

        fs.withContext("user1") {
            fs.mkdir(it, "/home/user1/folder/./../folder/./Weirdpath")
        }

        val existingFolder2 = File(fsRoot, "home/user1/folder/Weirdpath")
        Assert.assertTrue(existingFolder2.exists())
    }


}