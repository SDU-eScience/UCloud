package dk.sdu.cloud.storage.services

import dk.sdu.cloud.file.services.BulkDownloadService
import dk.sdu.cloud.file.services.CoreFileSystemService
import dk.sdu.cloud.file.services.FileSensitivityService
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunner
import dk.sdu.cloud.file.services.linuxfs.LinuxFSRunnerFactory
import dk.sdu.cloud.file.services.withBlockingContext
import dk.sdu.cloud.service.test.ClientMock
import dk.sdu.cloud.storage.util.linuxFSWithRelaxedMocks
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPInputStream

class BulkDownloadTest {
    fun File.mkdir(name: String, closure: File.() -> Unit) {
        val f = File(this, name)
        f.mkdir()
        f.closure()
    }

    fun File.touch(name: String) {
        File(this, name).writeText("Hello!")
    }

    fun createFileSystem(): File {
        val fsRoot = Files.createTempDirectory("bulk-download-service-test").toFile()
        fsRoot.apply {
            mkdir("home") {
                (1..10).map { "user$it" }.forEach {
                    mkdir(it) {
                        mkdir("PleaseShare") {
                            repeat(100) { touch("file$it.txt") }
                        }
                    }
                }
            }
        }
        return fsRoot
    }

    private fun createService(root: String):
            Pair<LinuxFSRunnerFactory, BulkDownloadService<LinuxFSRunner>> {
        val (runner, fs) = linuxFSWithRelaxedMocks(root)
        val fileSensitivityService = mockk<FileSensitivityService<LinuxFSRunner>>()
        val coreFs =
            CoreFileSystemService(fs, mockk(relaxed = true), fileSensitivityService, ClientMock.authenticatedClient)

        return Pair(runner, BulkDownloadService(coreFs))
    }

    @Test
    fun testSimpleDownload() {
        val fsRoot = createFileSystem()
        val (runner, service) = createService(fsRoot.absolutePath)
        val out = ByteArrayOutputStream()
        runner.withBlockingContext("user1") {
            service.downloadFiles(it, "/home/user1/PleaseShare", (1..10).map { "file$it.txt" }, out)
        }

        val readBuffer = ByteArray(1024 * 1024)
        TarInputStream(GZIPInputStream(ByteArrayInputStream(out.toByteArray()))).use {
            var idx = 1
            var entry: TarEntry? = it.nextEntry
            while (entry != null) {
                assertEquals("file$idx.txt", entry.name)
                assertEquals(entry.size.toInt(), it.read(readBuffer, 0, entry.size.toInt()))
                assertEquals("Hello!", String(readBuffer, 0, entry.size.toInt()))
                entry = it.nextEntry
                idx++
            }
            assertEquals(11, idx)
        }
    }

    @Test
    fun testSimpleDownloadWithMissingFiles() {
        val fsRoot = createFileSystem()
        val (runner, service) = createService(fsRoot.absolutePath)

        val out = ByteArrayOutputStream()
        runner.withBlockingContext("user1") {
            service.downloadFiles(
                it,
                "/home/user1/PleaseShare",
                (1..10).map { "file$it.txt" } + listOf("notafile.txt"),
                out)
        }

        val readBuffer = ByteArray(1024 * 1024)
        TarInputStream(GZIPInputStream(ByteArrayInputStream(out.toByteArray()))).use {
            var idx = 1
            var entry: TarEntry? = it.nextEntry
            while (entry != null) {
                assertEquals("file$idx.txt", entry.name)
                assertEquals(entry.size.toInt(), it.read(readBuffer, 0, entry.size.toInt()))
                assertEquals("Hello!", String(readBuffer, 0, entry.size.toInt()))
                entry = it.nextEntry
                idx++
            }
            assertEquals(11, idx)
        }
    }
}
