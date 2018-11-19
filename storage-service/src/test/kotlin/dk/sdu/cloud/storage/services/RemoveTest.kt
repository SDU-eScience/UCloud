package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunner
import dk.sdu.cloud.file.services.unixfs.UnixFSCommandRunnerFactory
import dk.sdu.cloud.file.services.withContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.storage.util.unixFSWithRelaxedMocks
import dk.sdu.cloud.storage.util.createDummyFS
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RemoveTest {
    private fun createService(
        root: String,
        emitter: StorageEventProducer = mockk(relaxed = true)
    ): Pair<UnixFSCommandRunnerFactory, CoreFileSystemService<UnixFSCommandRunner>> {
        val (runner, fs) = unixFSWithRelaxedMocks(root)
        return Pair(runner, CoreFileSystemService(fs, emitter))
    }

    @Test
    fun testSimpleRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        runner.withContext("user1") {
            service.delete(it, "/home/user1/folder")
        }
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

    @Test(expected = FSException.NotFound::class)
    fun testNonExistingPathRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.emit(any()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        //Folder should not exists
        val nonExistingFolder = File(fsRoot, "home/user1/fold")
        assertFalse(nonExistingFolder.exists())
        runner.withContext("user1") { service.delete(it, "/home/user1/fold") }
    }
}
