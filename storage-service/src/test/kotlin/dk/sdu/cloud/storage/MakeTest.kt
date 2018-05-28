package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.FileSystemException
import dk.sdu.cloud.storage.services.cephfs.RemoveService
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MakeTest {

    fun createFileSystem(): File {
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
                }
            }
        }
        return fsRoot
    }

    @Test (expected = FileSystemException.AlreadyExists::class)
    fun testNewDirAlreadyExists() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createFileSystem()
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

        val fsRoot = createFileSystem()
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