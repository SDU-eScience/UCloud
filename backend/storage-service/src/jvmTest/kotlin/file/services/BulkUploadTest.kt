package dk.sdu.cloud.file.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.OutgoingHttpCall
import dk.sdu.cloud.file.api.WriteConflictPolicy
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.micro.client
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.service.test.assertCollectionHasItem
import dk.sdu.cloud.service.test.assertThatPropertyEquals
import dk.sdu.cloud.service.test.initializeMicro
import dk.sdu.cloud.file.util.linuxFSWithRelaxedMocks
import dk.sdu.cloud.micro.BackgroundScope
import dk.sdu.cloud.notification.api.FindByNotificationId
import dk.sdu.cloud.notification.api.NotificationDescriptions
import io.mockk.mockk
import junit.framework.Assert.*
import org.junit.Test
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarHeader
import org.kamranzafar.jtar.TarOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.*

class BulkUploadTest : WithBackgroundScope() {
    val cloud = ClientMock.authenticatedClient

    fun File.mkdir(name: String, closure: File.() -> Unit) {
        val f = File(this, name)
        f.mkdir()
        f.closure()
    }

    fun File.touch(name: String, contents: String) {
        File(this, name).writeText(contents)
    }

    fun createFileSystem(closure: File.() -> Unit): File {
        val fsRoot = Files.createTempDirectory("share-service-test").toFile()
        fsRoot.closure()
        return fsRoot
    }

    fun TarOutputStream.putDirectory(name: String) {
        putNextEntry(
            TarEntry(
                TarHeader.createHeader(
                    name, 0, 0, true, 511 // 0777
                )
            )
        )
    }

    fun TarOutputStream.putFile(name: String, contents: String) {
        val payload = contents.toByteArray()
        putNextEntry(
            TarEntry(
                TarHeader.createHeader(
                    name, payload.size.toLong(), 0, false, 511
                )
            )
        )

        write(payload)
    }

    fun createTarFile(target: OutputStream, closure: TarOutputStream.() -> Unit) {
        TarOutputStream(GZIPOutputStream(target)).use {
            it.closure()
        }
    }

    private fun createService(root: String): Pair<LinuxFSRunnerFactory, CoreFileSystemService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root, backgroundScope)
        val coreFs =
            CoreFileSystemService(
                fs,
                ClientMock.authenticatedClient,
                backgroundScope,
                mockedMetadataService,
                mockk(relaxed = true)
            )
        return Pair(runner, coreFs)
    }

    @Test
    fun testSimpleUpload() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/file", "hello!")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                cloud,
                service,
                { it },
                "/home/user/",
                WriteConflictPolicy.OVERWRITE,
                tarFile.inputStream(),
                null,
                "",
                backgroundScope
            )
        }

        val homeDir = File(fsRoot, "/home/user")
        assertTrue(homeDir.exists())

        val testDir = File(homeDir, "test")
        assertTrue(testDir.exists())
        assertTrue(testDir.isDirectory)

        val testFile = File(testDir, "file")
        assertTrue(testFile.exists())
        assertFalse(testFile.isDirectory)
        assertEquals("hello!", testFile.readText())
    }

    @Test
    fun testRename() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )
        val originalContents = "original"
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                    mkdir("test") {
                        touch("file", originalContents)
                    }
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/file", "hello!")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { it },
                    "/home/user/",
                    WriteConflictPolicy.RENAME,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "file")
            assertTrue(origTestFile.exists())
            assertFalse(origTestFile.isDirectory)
            assertEquals(originalContents, origTestFile.readText())

            val testFile = File(testDir, "file(1)")
            assertTrue(testFile.exists())
            assertFalse(testFile.isDirectory)
            assertEquals("hello!", testFile.readText())

            assertEquals(2, result.size)
        }
    }

    @Test
    fun testOverwrite() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )
        val originalContents = "original"
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                    mkdir("test") {
                        touch("file", originalContents)
                    }
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/file", "hello!")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { it },
                    "/home/user/",
                    WriteConflictPolicy.OVERWRITE,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "file")
            assertTrue(origTestFile.exists())
            assertFalse(origTestFile.isDirectory)
            assertEquals("hello!", origTestFile.readText())

            assertEquals(2, result.size)
        }
    }

    @Test
    fun testReject() {
        val originalContents = "original"
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                    mkdir("test") {
                        touch("file", originalContents)
                    }
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/file", "hello!")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { it },
                    "/home/user/",
                    WriteConflictPolicy.REJECT,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "file")
            assertTrue(origTestFile.exists())
            assertFalse(origTestFile.isDirectory)
            assertEquals(originalContents, origTestFile.readText())

            assertEquals(2, result.size)
            assertEquals(listOf("/home/user/test", "/home/user/test"), result)
        }
    }

    @Test
    fun testFromFileToDir() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )
        val originalContents = "original"
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                    mkdir("test") {
                        touch("file", originalContents)
                    }
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putDirectory("test/file")
            putFile("test/file/foo", "contents")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") { ctx ->
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { ctx },
                    "/home/user/",
                    WriteConflictPolicy.OVERWRITE,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "file")
            assertTrue(origTestFile.exists())
            assertFalse(origTestFile.isDirectory)
            assertEquals(originalContents, origTestFile.readText())

            assertThatPropertyEquals(result, { it.size }, 6)
            assertCollectionHasItem(result, matcher = { it == "/home/user/test/file/foo" })
        }
    }

    @Test
    fun testFromDirToFile() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )

        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {
                    mkdir("test") {
                        mkdir("file") {}
                    }
                }
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/file", "contents")
        }

        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { it },
                    "/home/user/",
                    WriteConflictPolicy.OVERWRITE,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            result.forEach { entry -> println(entry) }

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "file")
            assertTrue(origTestFile.exists())
            assertTrue(origTestFile.isDirectory)

            assertThatPropertyEquals(result, { it.size }, 3)
            assertCollectionHasItem(result, matcher = { it == "/home/user/test/file" })
        }
    }

    @Test
    fun testShellInjection() {
        ClientMock.mockCallSuccess(
            NotificationDescriptions.create,
            FindByNotificationId(1)
        )
        val fsRoot = createFileSystem {
            mkdir("home") {
                mkdir("user") {}
            }
        }

        val tarFile = Files.createTempFile("foo", ".tar.gz").toFile()
        createTarFile(tarFile.outputStream()) {
            putDirectory("test")
            putFile("test/\$PWD", "contents")
        }
        val (runner, service) = createService(fsRoot.absolutePath)
        runner.withBlockingContext("user") {
            val result =
                BulkUploader.fromFormat("tgz", LinuxFSRunner::class)!!.upload(
                    cloud,
                    service,
                    { it },
                    "/home/user/",
                    WriteConflictPolicy.OVERWRITE,
                    tarFile.inputStream(),
                    null,
                    "",
                    backgroundScope
                )

            val homeDir = File(fsRoot, "/home/user")
            assertTrue(homeDir.exists())

            val testDir = File(homeDir, "test")
            assertTrue(testDir.exists())
            assertTrue(testDir.isDirectory)

            val origTestFile = File(testDir, "\$PWD")
            assertTrue(origTestFile.exists())
            assertFalse(origTestFile.isDirectory)

            assertEquals(0, result.size)
        }
    }
}
