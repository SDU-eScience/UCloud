package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.StorageEvent
import dk.sdu.cloud.storage.api.StorageEventProducer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RemoveTest {

    @Test
    fun testSimpleRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
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

    @Test(expected = FSException.PermissionException::class)
    fun testNonExistingPathRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val fs = cephFSWithRelaxedMocks(
            fsRoot.absolutePath,
            eventProducer = emitter
        )

        //Folder should not exists
        val nonExistingFolder = File(fsRoot, "home/user1/fold")
        assertFalse(nonExistingFolder.exists())
        fs.rmdir(fs.openContext("user1"), "/home/user1/fold")
    }
}