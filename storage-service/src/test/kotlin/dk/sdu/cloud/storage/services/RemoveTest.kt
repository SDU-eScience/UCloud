package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEvents
import dk.sdu.cloud.file.api.path
import dk.sdu.cloud.file.services.background.BackgroundScope
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.StorageEventProducer
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.CloudMock
import dk.sdu.cloud.service.test.EventServiceMock
import dk.sdu.cloud.storage.util.createDummyFS
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class RemoveTest {
    private fun createService(
        root: String,
        emitter: StorageEventProducer = mockk(relaxed = true)
    ): Pair<LinuxFSRunnerFactory, CoreFileSystemService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        return Pair(runner, CoreFileSystemService(fs, emitter, fileSensitivityService, ClientMock.authenticatedClient))
    }

    @Test
    fun testSimpleRemove() {
        BackgroundScope.reset()
        EventServiceMock.reset()
        try {
            val emitter = StorageEventProducer(EventServiceMock.createProducer(StorageEvents.events), {})

            val fsRoot = createDummyFS()
            val (runner, service) = createService(fsRoot.absolutePath, emitter)

            runner.withBlockingContext("user1") {
                service.delete(it, "/home/user1/folder")
            }
            val existingFolder = File(fsRoot, "home/user1/folder")
            assertFalse(existingFolder.exists())

            // The function returns immediately. We want to wait for those events to have been emitted.
            // This is not a fool proof way of doing it. But we have no way of waiting for tasks
            Thread.sleep(100)

            val events = EventServiceMock.messagesForTopic(StorageEvents.events)

            events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/a" }
            events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/b" }
            events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder/c" }
            events.any { it is StorageEvent.Deleted && it.file.path == "/home/user1/folder" }
        } finally {
            BackgroundScope.stop()
        }
    }

    @Test(expected = FSException.NotFound::class)
    fun testNonExistingPathRemove() {
        val emitter: StorageEventProducer = mockk()
        coEvery { emitter.produce(any<StorageEvent>()) } coAnswers {
            println("Hello! ${it.invocation.args.first()}}")
        }

        val fsRoot = createDummyFS()
        val (runner, service) = createService(fsRoot.absolutePath, emitter)

        //Folder should not exists
        val nonExistingFolder = File(fsRoot, "home/user1/fold")
        assertFalse(nonExistingFolder.exists())
        runner.withBlockingContext("user1") { service.delete(it, "/home/user1/fold") }
    }
}
