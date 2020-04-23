@file:Suppress("BlockingMethodInNonBlockingContext")

package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.linuxfs.LinuxFS
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.util.FSException
import dk.sdu.cloud.file.util.createDummyFS
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.TestUsers
import io.mockk.mockk
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.*

class UploadTest : WithBackgroundScope() {
    private lateinit var runner: LinuxFSRunnerFactory
    private lateinit var fs: LinuxFS
    private lateinit var coreFs: CoreFileSystemService<LinuxFSRunner>

    @BeforeTest
    fun beforeTest() {
        val linuxFs = linuxFSWithRelaxedMocks(createDummyFS().absolutePath, backgroundScope)
        runner = linuxFs.runner
        fs = linuxFs.fs
        coreFs = CoreFileSystemService(
            fs,
            ClientMock.authenticatedClient,
            backgroundScope,
            mockedMetadataService
        )
    }

    @Test
    fun `test uploading simple file`() {
        runner.withBlockingContext(TestUsers.user.username) { ctx ->
            coreFs.write(ctx, "/home/user/simple-file.txt", WriteConflictPolicy.OVERWRITE) {
                write("Hello, World!".toByteArray())
            }
        }
    }

    @Test(expected = FSException.IsDirectoryConflict::class)
    fun `test uploading file to file`() {
        runner.withBlockingContext(TestUsers.user.username) { ctx ->
            coreFs.write(ctx, "/home/user/folder", WriteConflictPolicy.OVERWRITE) {
                write("This should not work".toByteArray())
            }
        }
    }

    @Test(expected = FSException.NotFound::class)
    fun `test uploading file to non-existing parent`() {
        runner.withBlockingContext(TestUsers.user.username) { ctx ->
            coreFs.write(ctx, "/home/user/folder/f/f/f/f", WriteConflictPolicy.OVERWRITE) {
                write("This should not work".toByteArray())
            }
        }
    }
}
