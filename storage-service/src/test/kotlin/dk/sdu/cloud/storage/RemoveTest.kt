package dk.sdu.cloud.storage

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import dk.sdu.cloud.storage.services.cephfs.RemoveService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoveTest {
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

    @Test
    fun testSimpleRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createFileSystem()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            removeService = RemoveService(true),
            eventProducer = emitter
        )

        fs.rmdir(fs.openContext("user1"), "/home/user1/folder")
        val existingFolder = File(fsRoot, "home/user1/folder")
        assertFalse(existingFolder.exists())

        // The function returns immediately. We want to wait for those events to have been emitted.
        // This is not a fool proof way of doing it. But we have no way of waiting for tasks
        Thread.sleep(100)

        coVerify {
            emitter.emit(match { it is StorageEvent.Deleted && it.path == "/home/user1/folder/a" })
            emitter.emit(match { it is StorageEvent.Deleted && it.path == "/home/user1/folder/b" })
            emitter.emit(match { it is StorageEvent.Deleted && it.path == "/home/user1/folder/c" })
            emitter.emit(match { it is StorageEvent.Deleted && it.path == "/home/user1/folder" })
        }
    }
}